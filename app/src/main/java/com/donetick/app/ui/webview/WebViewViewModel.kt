package com.donetick.app.ui.webview

import android.util.Log
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.donetick.app.domain.usecase.CheckServerConnectivityUseCase
import com.donetick.app.domain.usecase.GetServerConfigUseCase
import com.donetick.app.notification.ChoreNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * ViewModel for the WebView screen
 */
@HiltViewModel
class WebViewViewModel @Inject constructor(
    private val getServerConfigUseCase: GetServerConfigUseCase,
    private val checkServerConnectivityUseCase: CheckServerConnectivityUseCase,
    private val choreNotificationManager: ChoreNotificationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WebViewUiState.initial())
    val uiState: StateFlow<WebViewUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<WebViewNavigationEvent?>(null)
    val navigationEvent: StateFlow<WebViewNavigationEvent?> = _navigationEvent.asStateFlow()

    private var webView: WeakReference<WebView>? = null

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
        this.webView = WeakReference(webView)
        setupWebView()
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

    /**
     * Handles chores data received from WebView JavaScript
     */
    fun handleChoresData(jsonData: String) {
        try {
            // Check if this is the same data we already processed
            val currentData = _uiState.value.choresData
            if (currentData == jsonData) {
                return
            }

            val choresList = parseChoresJson(jsonData)

            _uiState.value = _uiState.value.copy(
                choresData = jsonData,
                choresList = choresList
            )

            // Schedule notifications for chores with notification enabled
            scheduleChoreNotifications(choresList)

        } catch (e: Exception) {
            Log.e("WebViewViewModel", "Error parsing chores data", e)
        }
    }

    /**
     * Schedules notifications for chores that have notification enabled
     */
    private fun scheduleChoreNotifications(chores: List<ChoreItem>) {
        viewModelScope.launch {
            try {
                choreNotificationManager.scheduleChoreNotifications(chores)
            } catch (e: Exception) {
                Log.e("WebViewViewModel", "Error scheduling notifications", e)
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
            Log.e("WebViewViewModel", "Error handling chore marked done", e)
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

    /**
     * Parses the JSON chores data into ChoreItem objects
     */
    private fun parseChoresJson(jsonData: String): List<ChoreItem> {
        return try {
            val jsonObject = JSONObject(jsonData)
            val resArray = jsonObject.optJSONArray("res") ?: JSONArray(jsonData)
            val choresList = mutableListOf<ChoreItem>()

            for (i in 0 until resArray.length()) {
                val choreJson = resArray.getJSONObject(i)

                // Parse notification metadata
                val notificationMetadata = choreJson.optJSONObject("notificationMetadata")?.let { metadata ->
                    NotificationMetadata(
                        dueDate = metadata.optBoolean("dueDate", false)
                    )
                }

                val chore = ChoreItem(
                    id = choreJson.optInt("id", 0),
                    name = choreJson.optString("name", ""),
                    assignedTo = choreJson.optInt("assignedTo").takeIf { it != 0 },
                    nextDueDate = choreJson.optString("nextDueDate").takeIf { it.isNotEmpty() },
                    isCompleted = choreJson.optInt("status", 0) == 1, // Assuming status 1 means completed
                    frequencyType = choreJson.optString("frequencyType").takeIf { it.isNotEmpty() },
                    frequency = choreJson.optInt("frequency", 1),
                    description = choreJson.optString("description").takeIf { it.isNotEmpty() },
                    notification = choreJson.optBoolean("notification", false),
                    notificationMetadata = notificationMetadata,
                    isActive = choreJson.optBoolean("isActive", true),
                    priority = choreJson.optInt("priority", 0)
                )

                choresList.add(chore)
            }

            choresList
        } catch (e: Exception) {
            Log.e("WebViewViewModel", "Error parsing chores JSON", e)
            emptyList()
        }
    }
}

/**
 * Navigation events for the WebView screen
 */
sealed class WebViewNavigationEvent {
    object NavigateToSetup : WebViewNavigationEvent()
    object NavigateToSettings : WebViewNavigationEvent()
}
