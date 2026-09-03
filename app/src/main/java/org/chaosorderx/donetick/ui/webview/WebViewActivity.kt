package org.chaosorderx.donetick.ui.webview

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import org.chaosorderx.donetick.notification.NotificationPermissionHelper
import org.chaosorderx.donetick.ui.settings.SettingsActivity
import org.chaosorderx.donetick.ui.setup.SetupActivity
import org.chaosorderx.donetick.ui.theme.DoneTickTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Main activity for displaying DoneTick server interface through WebView.
 *
 * This activity hosts the WebViewScreen and handles:
 * - WebView configuration and JavaScript injection
 * - API data capture for chores and notifications
 * - Navigation to ChoresListActivity via button click
 * - Standard WebView back navigation
 *
 * Architecture: Uses a simplified single-view approach instead of HorizontalPager
 * for better performance and cleaner navigation patterns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
    }

    private val viewModel: WebViewViewModel by viewModels()
    private var webView: WebView? = null

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Handle WebView back navigation or finish activity
            if (!viewModel.goBack()) {
                finish()
            }
        }
    }

    // Permission launcher for notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, notifications can now be scheduled
            viewModel.onNotificationPermissionGranted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission using the new API
        NotificationPermissionHelper.requestNotificationPermission(this, notificationPermissionLauncher)

        // Register back button handler
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        // Observe navigation events
        lifecycleScope.launch {
            viewModel.navigationEvent.collect { event ->
                when (event) {
                    is WebViewNavigationEvent.NavigateToSetup -> {
                        navigateToSetup()
                        viewModel.clearNavigationEvent()
                    }
                    is WebViewNavigationEvent.NavigateToSettings -> {
                        navigateToSettings()
                        viewModel.clearNavigationEvent()
                    }
                    null -> {
                        // No navigation event
                    }
                }
            }
        }

        setContent {
            DoneTickTheme {
                val uiState by viewModel.uiState.collectAsState()

                WebViewScreen(
                    uiState = uiState,
                    onRefresh = viewModel::refresh,
                    onMenuClick = viewModel::showSettings,
                    onChoresClick = {
                        // Launch ChoresListActivity with current chores data
                        val intent = ChoresListActivity.createIntent(this@WebViewActivity, uiState.choresList)
                        startActivity(intent)
                    },
                    onWebViewCreated = { webView ->
                        this@WebViewActivity.webView = webView
                        setupWebView(webView)
                        viewModel.setWebView(webView)
                    }
                )
            }
        }
    }

    /**
     * Sets up WebView with proper configuration and clients
     */
    private fun setupWebView(webView: WebView) {

        webView.settings.apply {
            cacheMode = WebSettings.LOAD_DEFAULT // Use the default caching strategy
        }

        // Use a hardware layer to enable GPU rendering
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Add JavaScript interface for capturing API data
        webView.addJavascriptInterface(ApiDataCapture(), "AndroidApiCapture")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                viewModel.updateLoadingState(true)
                viewModel.clearError()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                viewModel.updateLoadingState(false)
                viewModel.updateCanGoBack(view?.canGoBack() ?: false)

                // Inject JavaScript to intercept API calls
                injectApiInterceptorScript(view)
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val errorMessage = error?.description?.toString() ?: "Failed to load page"
                viewModel.handleWebViewError(errorMessage)
            }

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                viewModel.handleWebViewError(
                    description ?: "Failed to load page"
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                viewModel.updateProgress(newProgress)
                viewModel.updateLoadingState(newProgress < 100)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { viewModel.updatePageTitle(it) }
            }
        }
    }

    /**
     * Injects JavaScript to intercept API calls and capture responses
     */
    private fun injectApiInterceptorScript(webView: WebView?) {
        val script = """
            (function() {
                // The interceptor only needs to wrap fetch/XHR once per document.
                // onPageFinished can fire more than once for the same page, and
                // re-wrapping would stack wrappers. The active pull, however, should
                // run on every onPageFinished so a fresh page load re-syncs chores.
                if (window.__dtInterceptorInstalled) {
                    if (typeof window.__dtRefreshChores === 'function') {
                        window.__dtRefreshChores();
                    }
                    return;
                }
                window.__dtInterceptorInstalled = true;

                // Store original fetch function
                const originalFetch = window.fetch;

                function __dtUrlString(u) {
                    if (typeof u === 'string') return u;
                    if (u && typeof u.url === 'string') return u.url;
                    if (u && typeof u.href === 'string') return u.href;
                    return '';
                }

                function __dtApiBase() {
                    var customBase = null;
                    try { customBase = localStorage.getItem('customServerUrl'); } catch (e) {}
                    var base = (customBase && customBase.trim()) ? customBase.trim() : window.location.origin;
                    return base.replace(/\/+$/, '') + '/api/v1';
                }

                // Active chores pull.
                //
                // Newer DoneTick frontends moved to an offline-first sync engine
                // (GET /api/v1/sync/changes + IndexedDB) and no longer call
                // GET /api/v1/chores/ on every load, so the passive hooks below
                // rarely fire. Pull the full list directly and hand it to the same
                // Android handler; /api/v1/chores/ still returns { res: [...] }.
                // Uses originalFetch so it does not re-enter our own wrapper.
                window.__dtRefreshChores = async function() {
                    try {
                        var token = null;
                        try { token = localStorage.getItem('token'); } catch (e) {}
                        var headers = { 'Accept': 'application/json' };
                        if (token) { headers['Authorization'] = 'Bearer ' + token; }

                        var res = await originalFetch(__dtApiBase() + '/chores/', {
                            method: 'GET',
                            headers: headers,
                            credentials: 'include'
                        });
                        if (!res || !res.ok) {
                            console.log('dtRefreshChores: skipped, status', res && res.status);
                            return;
                        }
                        var text = await res.text();
                        try {
                            AndroidApiCapture.onChoresDataReceived(text);
                        } catch (e) {
                            console.error('dtRefreshChores: bridge error', e);
                        }
                    } catch (e) {
                        console.log('dtRefreshChores: error', e);
                    }
                };

                // Debounced trigger: several events in quick succession collapse
                // into a single pull.
                var __dtRefreshTimer = null;
                function __dtScheduleRefresh() {
                    if (__dtRefreshTimer) return;
                    __dtRefreshTimer = setTimeout(function() {
                        __dtRefreshTimer = null;
                        window.__dtRefreshChores();
                    }, 800);
                }

                // Re-pull whenever the frontend does something that changes chore
                // state or auth: a sync, a login, or a per-chore action. This covers
                // the "logged in after the page finished loading" case where
                // onPageFinished never fires again.
                function __dtMaybeTriggerRefresh(url) {
                    if (!url) return;
                    if (url.match(/\/api\/v1\/sync\/changes/) ||
                        url.match(/\/api\/v1\/auth\//) ||
                        url.match(/\/api\/v[i1]\/chores\/\d+\/(do|skip|complete|archive|start|pause|update)/)) {
                        __dtScheduleRefresh();
                    }
                }

                // Override fetch to intercept API calls
                window.fetch = function(...args) {
                    const url = __dtUrlString(args[0]);

                    return originalFetch.apply(this, args)
                        .then(response => {
                            try {
                                // Exact chores list call (still works when offline mode is off)
                                if (url.match(/\/api\/v[i1]\/chores\/?(\?.*)?$/)) {
                                    response.clone().text().then(text => {
                                        try { AndroidApiCapture.onChoresDataReceived(text); }
                                        catch (e) { console.error('Error sending chores data to Android:', e); }
                                    }).catch(e => console.error('Error reading chores response:', e));
                                }
                                // Chore "do" action -> cancel that chore's notification
                                else if (url.match(/\/api\/v[i1]\/chores\/(\d+)\/do/)) {
                                    const m = url.match(/\/api\/v[i1]\/chores\/(\d+)\/do/);
                                    if (m && m[1]) {
                                        try { AndroidApiCapture.onChoreMarkedDone(parseInt(m[1])); }
                                        catch (e) { console.error('Error notifying Android of chore done:', e); }
                                    }
                                }
                                __dtMaybeTriggerRefresh(url);
                            } catch (e) {
                                console.error('dt fetch hook error:', e);
                            }
                            return response;
                        });
                };

                // Also intercept XMLHttpRequest for older implementations
                const originalXHROpen = XMLHttpRequest.prototype.open;
                const originalXHRSend = XMLHttpRequest.prototype.send;

                XMLHttpRequest.prototype.open = function(method, url, ...rest) {
                    this._url = __dtUrlString(url);
                    return originalXHROpen.apply(this, [method, url, ...rest]);
                };

                XMLHttpRequest.prototype.send = function(...args) {
                    this.addEventListener('load', function() {
                        try {
                            const u = this._url;
                            if (!u) return;
                            if (u.match(/\/api\/v[i1]\/chores\/?(\?.*)?$/)) {
                                AndroidApiCapture.onChoresDataReceived(this.responseText);
                            }
                            else if (u.match(/\/api\/v[i1]\/chores\/(\d+)\/do/)) {
                                const m = u.match(/\/api\/v[i1]\/chores\/(\d+)\/do/);
                                if (m && m[1]) { AndroidApiCapture.onChoreMarkedDone(parseInt(m[1])); }
                            }
                            __dtMaybeTriggerRefresh(u);
                        } catch (e) {
                            console.error('dt xhr hook error:', e);
                        }
                    });
                    return originalXHRSend.apply(this, args);
                };

                // Initial pull for this page load.
                window.__dtRefreshChores();
            })();
        """.trimIndent()

        webView?.evaluateJavascript(script, null)
    }

    /**
     * JavaScript interface for capturing API data
     */
    inner class ApiDataCapture {
        @android.webkit.JavascriptInterface
        fun onChoresDataReceived(jsonData: String) {
            // Run on UI thread since this is called from JavaScript thread
            runOnUiThread {
                android.util.Log.d("WebViewActivity", "onChoresDataReceived called with data length: ${jsonData.length}")
                viewModel.handleChoresData(jsonData)
            }
        }

        @android.webkit.JavascriptInterface
        fun onChoreMarkedDone(choreId: Int) {
            // Run on UI thread since this is called from JavaScript thread
            runOnUiThread {
                android.util.Log.d("WebViewActivity", "onChoreMarkedDone called with choreId: $choreId")
                viewModel.handleChoreMarkedDone(choreId)
            }
        }
    }

    /**
     * Navigates to setup activity
     */
    private fun navigateToSetup() {
        val intent = Intent(this, SetupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    /**
     * Navigates to settings activity
     */
    private fun navigateToSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }



    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}
