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

                // Small JS hook for instant per-chore notification cancellation + a login signal.
                injectApiInterceptorScript(view)
                // Native sync: pull /sync/changes with the WebView's JWT and reschedule.
                viewModel.onWebViewPageFinished()
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
     * Injects a small JS hook. The chores list itself comes from the native sync client
     * ([WebViewViewModel.onWebViewPageFinished]); this only watches for two things the native
     * side can't see:
     *  - `POST /api/v1/chores/{id}/do` — so a chore's notification cancels the instant the user
     *    taps "done" in the web UI, without waiting for the next sync cycle
     *  - `/api/v1/auth/` calls — a login that happens without a full page reload, after which
     *    `onPageFinished` never fires again
     */
    private fun injectApiInterceptorScript(webView: WebView?) {
        val script = """
            (function() {
                if (window.__dtHooksInstalled) return;
                window.__dtHooksInstalled = true;

                function u(x) {
                    if (typeof x === 'string') return x;
                    if (x && typeof x.url === 'string') return x.url;
                    if (x && typeof x.href === 'string') return x.href;
                    return '';
                }
                function inspect(url) {
                    try {
                        if (!url) return;
                        var m = url.match(/\/api\/v[i1]\/chores\/(\d+)\/do/);
                        if (m && m[1]) { AndroidApiCapture.onChoreMarkedDone(parseInt(m[1])); return; }
                        if (url.match(/\/api\/v1\/auth\//)) { AndroidApiCapture.onAuthActivity(); }
                    } catch (e) { console.error('dt hook error', e); }
                }

                var of = window.fetch;
                window.fetch = function() {
                    var url = u(arguments[0]);
                    return of.apply(this, arguments).then(function(r) { inspect(url); return r; });
                };

                var xo = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this.__dtUrl = u(url);
                    return xo.apply(this, arguments);
                };
                var xs = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function() {
                    var self = this;
                    this.addEventListener('load', function() { inspect(self.__dtUrl); });
                    return xs.apply(this, arguments);
                };
            })();
        """.trimIndent()

        webView?.evaluateJavascript(script, null)
    }

    /**
     * JavaScript bridge for the small hook injected by [injectApiInterceptorScript].
     */
    inner class ApiDataCapture {
        @android.webkit.JavascriptInterface
        fun onChoreMarkedDone(choreId: Int) {
            runOnUiThread { viewModel.handleChoreMarkedDone(choreId) }
        }

        @android.webkit.JavascriptInterface
        fun onAuthActivity() {
            runOnUiThread { viewModel.onWebAuthActivity() }
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
