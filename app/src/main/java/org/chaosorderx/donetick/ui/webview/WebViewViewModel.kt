package org.chaosorderx.donetick.ui.webview

import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.chaosorderx.donetick.data.mapper.ChoreJsonMapper
import org.chaosorderx.donetick.data.sync.ChoreSyncCoordinator
import org.chaosorderx.donetick.domain.usecase.CheckServerConnectivityUseCase
import org.chaosorderx.donetick.domain.usecase.GetServerConfigUseCase
import org.chaosorderx.donetick.notification.ChoreNotificationManager
import org.chaosorderx.donetick.notification.SessionExpiryNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Reads the Donetick JWT out of the WebView's page storage. The app stays a thin wrapper:
 * the user logs in normally in the WebView, and native sync borrows that token.
 */
interface JwtReader {
    suspend fun read(): String?
}

/**
 * The WebView's login session: reads when the token expires and refreshes it. Refresh runs
 * inside the page so the HttpOnly refresh cookie is sent exactly as the web client sends it.
 */
interface SessionBridge {
    /** Token expiry in epoch millis, or null if unknown / not logged in. */
    suspend fun readTokenExpiryMillis(): Long?

    /** Calls `/auth/refresh` and stores the new tokens; true on success. */
    suspend fun refreshToken(): Boolean
}

/**
 * ViewModel for the WebView screen
 */
@HiltViewModel
class WebViewViewModel @Inject constructor(
    private val getServerConfigUseCase: GetServerConfigUseCase,
    private val checkServerConnectivityUseCase: CheckServerConnectivityUseCase,
    private val choreNotificationManager: ChoreNotificationManager,
    private val choreSyncCoordinator: ChoreSyncCoordinator,
    private val sessionExpiryNotifier: SessionExpiryNotifier
) : ViewModel() {

    private companion object {
        const val TAG = "WebViewViewModel"
        const val AUTH_ACTIVITY_DEBOUNCE_MS = 800L
        const val REFRESH_TIMEOUT_MS = 15_000L

        /**
         * `/auth/refresh` requires `{"refresh_token": ...}` in the body (the cookie alone gets a
         * 400). The token is kept in localStorage by the hook in WebViewActivity.
         */
        const val REFRESH_SCRIPT = """
            (async function() {
                try {
                    var rt = localStorage.getItem('refresh_token');
                    if (!rt) { AndroidApiCapture.onSessionRefreshed(false); return; }
                    var r = await fetch('/api/v1/auth/refresh', {
                        method: 'POST',
                        credentials: 'include',
                        headers: {
                            'Content-Type': 'application/json',
                            'Authorization': 'Bearer ' + localStorage.getItem('token')
                        },
                        body: JSON.stringify({ refresh_token: rt })
                    });
                    if (!r.ok) { AndroidApiCapture.onSessionRefreshed(false); return; }
                    var j = await r.json();
                    var t = j.token || j.access_token;
                    if (!t) { AndroidApiCapture.onSessionRefreshed(false); return; }
                    localStorage.setItem('token', t);
                    var e = j.expire || j.access_token_expiry;
                    if (e) localStorage.setItem('token_expiry', e);
                    if (j.refresh_token) localStorage.setItem('refresh_token', j.refresh_token);
                    if (j.refresh_token_expiry) localStorage.setItem('refresh_token_expiry', j.refresh_token_expiry);
                    AndroidApiCapture.onSessionRefreshed(true);
                } catch (err) {
                    AndroidApiCapture.onSessionRefreshed(false);
                }
            })();
        """
    }

    private val _uiState = MutableStateFlow(WebViewUiState.initial())
    val uiState: StateFlow<WebViewUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<WebViewNavigationEvent?>(null)
    val navigationEvent: StateFlow<WebViewNavigationEvent?> = _navigationEvent.asStateFlow()

    private var webView: WeakReference<WebView>? = null

    /** Overridable for tests; the production reader is installed in [setWebView]. */
    var jwtReader: JwtReader? = null

    /** Overridable for tests; the production bridge is installed in [setWebView]. */
    var sessionBridge: SessionBridge? = null

    private var pendingRefresh: CompletableDeferred<Boolean>? = null

    private var syncJob: Job? = null
    private var lastScheduledChores: List<ChoreItem> = emptyList()

    init {
        loadServerConfig()
    }

    /**
     * Loads the current server configuration
     */
    private fun loadServerConfig() {
        viewModelScope.launch {
            try {
                val config = getServerConfigUseCase.getCurrentConfig()
                if (config.isConfigured && config.url.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        serverUrl = config.getNormalizedUrl(),
                        isLoading = false
                    )
                } else {
                    // No server configured, navigate to setup
                    _navigationEvent.value = WebViewNavigationEvent.NavigateToSetup
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load server configuration: ${e.message}"
                )
            }
        }
    }

    /**
     * Sets the WebView instance
     */
    fun setWebView(webView: WebView) {
        val ref = WeakReference(webView)
        this.webView = ref
        if (jwtReader == null) jwtReader = WebViewJwtReader(ref)
        if (sessionBridge == null) sessionBridge = WebViewSessionBridge(ref)
        setupWebView()
    }

    /** Reads `localStorage['token']` from the page on the main thread. */
    private class WebViewJwtReader(
        private val webViewRef: WeakReference<WebView>
    ) : JwtReader {
        override suspend fun read(): String? = withContext(Dispatchers.Main) {
            val wv = webViewRef.get() ?: return@withContext null
            suspendCancellableCoroutine { cont ->
                wv.evaluateJavascript(
                    "(function(){try{return localStorage.getItem('token')}catch(e){return null}})()"
                ) { raw ->
                    val token = raw
                        ?.trim()
                        ?.removeSurrounding("\"")
                        ?.takeIf { it.isNotEmpty() && it != "null" }
                    cont.resume(token)
                }
            }
        }
    }

    /** Reads the expiry from `localStorage` and refreshes by running `/auth/refresh` in the page. */
    private inner class WebViewSessionBridge(
        private val webViewRef: WeakReference<WebView>
    ) : SessionBridge {
        override suspend fun readTokenExpiryMillis(): Long? = withContext(Dispatchers.Main) {
            val wv = webViewRef.get() ?: return@withContext null
            suspendCancellableCoroutine { cont ->
                wv.evaluateJavascript(
                    "(function(){try{return localStorage.getItem('token_expiry')}catch(e){return null}})()"
                ) { raw ->
                    val text = raw?.trim()?.removeSurrounding("\"")
                    cont.resume(runCatching { Instant.parse(text).toEpochMilli() }.getOrNull())
                }
            }
        }

        override suspend fun refreshToken(): Boolean {
            val deferred = CompletableDeferred<Boolean>().also { pendingRefresh = it }
            withContext(Dispatchers.Main) {
                webViewRef.get()?.evaluateJavascript(REFRESH_SCRIPT, null)
                    ?: deferred.complete(false)
            }
            return withTimeoutOrNull(REFRESH_TIMEOUT_MS) { deferred.await() } ?: false
        }
    }

    /** Called by the JS bridge when the in-page refresh finishes. */
    fun onSessionRefreshResult(success: Boolean) {
        pendingRefresh?.complete(success)
    }

    /**
     * Configures the WebView settings and loads the URL
     */
    private fun setupWebView() {
        webView?.get()?.let { wv ->
            wv.settings.apply {
                // JavaScript is required for the DoneTick server interface functionality
                // The server uses JavaScript for dynamic content and API interactions
                javaScriptEnabled = true
                domStorageEnabled = true
                // Note: databaseEnabled was removed as it's deprecated (WebSQL Database is no longer supported)
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            // Enable autofill so password managers can detect the login form
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                wv.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            }
            // Expose virtual view structure to accessibility-based password managers (Bitwarden, 1Password, etc.)
            wv.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            wv.requestFocus()

            // Load the server URL
            val serverUrl = _uiState.value.serverUrl
            if (serverUrl.isNotEmpty()) {
                wv.loadUrl(serverUrl)
            }
        }
    }

    /**
     * Refreshes the WebView
     */
    fun refresh() {
        webView?.get()?.let { wv ->
            wv.reload()
        } ?: run {
            // If WebView is not available, reload server config
            loadServerConfig()
        }
    }

    /**
     * Handles back navigation in WebView
     */
    fun goBack(): Boolean {
        return webView?.get()?.let { wv ->
            if (wv.canGoBack()) {
                wv.goBack()
                true
            } else {
                false
            }
        } ?: false
    }

    /**
     * Updates loading state
     */
    fun updateLoadingState(isLoading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = isLoading)
    }

    /**
     * Updates page title
     */
    fun updatePageTitle(title: String) {
        _uiState.value = _uiState.value.copy(pageTitle = title)
    }

    /**
     * Updates progress
     */
    fun updateProgress(progress: Int) {
        _uiState.value = _uiState.value.copy(progress = progress)
    }

    /**
     * Updates can go back state
     */
    fun updateCanGoBack(canGoBack: Boolean) {
        _uiState.value = _uiState.value.copy(canGoBack = canGoBack)
    }

    /**
     * Handles WebView error
     */
    fun handleWebViewError(error: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            errorMessage = error
        )
    }

    /**
     * Clears error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Shows settings menu
     */
    fun showSettings() {
        _navigationEvent.value = WebViewNavigationEvent.NavigateToSettings
    }

    /**
     * Clears navigation event after handling
     */
    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }

    /**
     * Validates current server connectivity
     */
    fun validateServerConnectivity() {
        viewModelScope.launch {
            try {
                val result = checkServerConnectivityUseCase.validateCurrentServer()
                result.fold(
                    onSuccess = { isReachable ->
                        if (!isReachable) {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Server is not reachable. Please check your connection."
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            errorMessage = error.message ?: "Failed to validate server connectivity"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Network error: ${e.message}"
                )
            }
        }
    }

    /** Called from `WebViewClient.onPageFinished`. */
    fun onWebViewPageFinished() = triggerSync()

    /**
     * Called when the injected JS sees the frontend hit an `/auth/` endpoint — covers logging
     * in without a full page reload, where [onWebViewPageFinished] never fires again.
     */
    fun onWebAuthActivity() = triggerSync(debounceMs = AUTH_ACTIVITY_DEBOUNCE_MS)

    /**
     * Pulls the sync delta from the server (JWT borrowed from the WebView), updates the chores
     * list, and reschedules notifications when the notifiable set actually changed. Foreground only.
     */
    private fun triggerSync(debounceMs: Long = 0L) {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            if (jwtReader?.read().isNullOrEmpty()) {
                Log.d(TAG, "Sync skipped: no auth token yet (user not logged in)")
                return@launch
            }
            maintainSession()
            // Re-read: maintainSession may have swapped in a freshly refreshed token.
            val token = jwtReader?.read()
            if (token.isNullOrEmpty()) return@launch
            when (val outcome = choreSyncCoordinator.sync(token)) {
                is ChoreSyncCoordinator.Outcome.Success -> {
                    // The sync feed is the whole circle; the UI and the scheduler only care
                    // about chores that will actually produce a notification. This mirrors
                    // ChoreNotificationManager's own filter so the "Upcoming Notifications"
                    // list matches the alarms that get set.
                    val notifiable = ChoreJsonMapper.fromJsonList(outcome.chores)
                        .filter { it.notification && it.isActive && it.nextDueDate != null }
                    _uiState.value = _uiState.value.copy(choresList = notifiable)
                    if (notifiable != lastScheduledChores) {
                        scheduleChoreNotifications(notifiable)
                        lastScheduledChores = notifiable
                    }
                }
                ChoreSyncCoordinator.Outcome.Unauthorized ->
                    Log.i(TAG, "Sync got 401; retrying with a refreshed token next cycle")
                ChoreSyncCoordinator.Outcome.NoServer -> Unit
                is ChoreSyncCoordinator.Outcome.Failed ->
                    Log.w(TAG, "Sync failed", outcome.cause)
            }
        }
    }

    /**
     * Keeps the login from lapsing. Opening the app inside the 5-day warning window refreshes
     * the token; either way the warning alarm is (re)armed for 5 days before the current expiry.
     */
    private suspend fun maintainSession() {
        val bridge = sessionBridge ?: return
        var expiry = bridge.readTokenExpiryMillis() ?: return
        if (System.currentTimeMillis() >= SessionExpiryNotifier.warnAt(expiry)) {
            if (bridge.refreshToken()) {
                expiry = bridge.readTokenExpiryMillis() ?: expiry
            } else {
                Log.w(TAG, "Session refresh failed; user must reopen or sign in again")
            }
        }
        sessionExpiryNotifier.schedule(expiry)
    }

    /**
     * Schedules notifications for chores that have notification enabled
     */
    private fun scheduleChoreNotifications(chores: List<ChoreItem>) {
        viewModelScope.launch {
            try {
                choreNotificationManager.scheduleChoreNotifications(chores)
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling notifications", e)
            }
        }
    }

    /**
     * Called when notification permission is granted
     */
    fun onNotificationPermissionGranted() {
        // Re-schedule notifications if we have chores data
        val currentChores = _uiState.value.choresList
        if (currentChores.isNotEmpty()) {
            scheduleChoreNotifications(currentChores)
        }
    }

    /**
     * Handles when a chore is marked as done via the /do API endpoint
     */
    fun handleChoreMarkedDone(choreId: Int) {
        try {
            // Cancel the notification for this specific chore
            choreNotificationManager.cancelChoreNotification(choreId)

            // Update the local chores list to mark this chore as completed
            updateChoreCompletionStatus(choreId)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling chore marked done", e)
        }
    }

    /**
     * Updates the completion status of a specific chore in the local list
     */
    private fun updateChoreCompletionStatus(choreId: Int) {
        val currentState = _uiState.value
        val updatedChoresList = currentState.choresList.map { chore ->
            if (chore.id == choreId) {
                chore.copy(isCompleted = true)
            } else {
                chore
            }
        }

        _uiState.value = currentState.copy(choresList = updatedChoresList)
    }
}

/**
 * Navigation events for the WebView screen
 */
sealed class WebViewNavigationEvent {
    object NavigateToSetup : WebViewNavigationEvent()
    object NavigateToSettings : WebViewNavigationEvent()
}
