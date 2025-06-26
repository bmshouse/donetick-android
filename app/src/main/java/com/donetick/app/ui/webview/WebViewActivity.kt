package com.donetick.app.ui.webview

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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.donetick.app.notification.NotificationPermissionHelper
import com.donetick.app.ui.settings.SettingsActivity
import com.donetick.app.ui.setup.SetupActivity
import com.donetick.app.ui.theme.DoneTickTheme
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
                // Store original fetch function
                const originalFetch = window.fetch;

                // Override fetch to intercept API calls
                window.fetch = function(...args) {
                    const url = args[0];

                    // Log all API calls for debugging
                    if (url.includes('/api/')) {
                        console.log('API call detected:', url);
                    }

                    return originalFetch.apply(this, args)
                        .then(response => {
                            // Check if this is exactly the chores list API call
                            if (url.match(/\/api\/v[i1]\/chores\/?(\?.*)?$/)) {
                                // Clone response to avoid consuming it
                                const clonedResponse = response.clone();

                                clonedResponse.json().then(data => {
                                    try {
                                        // Send data to Android
                                        AndroidApiCapture.onChoresDataReceived(JSON.stringify(data));
                                    } catch (e) {
                                        console.error('Error sending chores data to Android:', e);
                                    }
                                }).catch(e => {
                                    console.error('Error parsing chores JSON:', e);
                                });
                            }
                            // Check if this is a chore "do" action (be more specific to avoid history)
                            else if (url.includes('/do') && (url.match(/\/api\/v[i1]\/chores\/\d+\/do/))) {
                                console.log('Detected chore do action for URL:', url);
                                // Extract chore ID from URL pattern /api/v1/chores/:id/do
                                const choreIdMatch = url.match(/\/api\/v[i1]\/chores\/(\d+)\/do/);
                                if (choreIdMatch && choreIdMatch[1]) {
                                    console.log('Extracted chore ID:', choreIdMatch[1]);
                                    try {
                                        AndroidApiCapture.onChoreMarkedDone(parseInt(choreIdMatch[1]));
                                    } catch (e) {
                                        console.error('Error notifying Android of chore done:', e);
                                    }
                                } else {
                                    console.log('Failed to extract chore ID from URL:', url);
                                }
                            }

                            return response;
                        });
                };

                // Also intercept XMLHttpRequest for older implementations
                const originalXHROpen = XMLHttpRequest.prototype.open;
                const originalXHRSend = XMLHttpRequest.prototype.send;

                XMLHttpRequest.prototype.open = function(method, url, ...args) {
                    this._url = url;
                    return originalXHROpen.apply(this, [method, url, ...args]);
                };

                XMLHttpRequest.prototype.send = function(...args) {
                    this.addEventListener('load', function() {
                        if (this._url) {
                            // Check if this is exactly the chores list API call
                            if (this._url.match(/\/api\/v[i1]\/chores\/?(\?.*)?$/)) {
                                try {
                                    const data = JSON.parse(this.responseText);
                                    AndroidApiCapture.onChoresDataReceived(JSON.stringify(data));
                                } catch (e) {
                                    console.error('Error parsing chores JSON from XHR:', e);
                                }
                            }
                            // Check if this is a chore "do" action (be more specific)
                            else if (this._url.includes('/do') && this._url.match(/\/api\/v[i1]\/chores\/\d+\/do/)) {
                                console.log('XHR: Detected chore do action for URL:', this._url);
                                // Extract chore ID from URL pattern /api/v1/chores/:id/do
                                const choreIdMatch = this._url.match(/\/api\/v[i1]\/chores\/(\d+)\/do/);
                                if (choreIdMatch && choreIdMatch[1]) {
                                    console.log('XHR: Extracted chore ID:', choreIdMatch[1]);
                                    try {
                                        AndroidApiCapture.onChoreMarkedDone(parseInt(choreIdMatch[1]));
                                    } catch (e) {
                                        console.error('Error notifying Android of chore done from XHR:', e);
                                    }
                                } else {
                                    console.log('XHR: Failed to extract chore ID from URL:', this._url);
                                }
                            }
                        }
                    });

                    return originalXHRSend.apply(this, args);
                };
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
