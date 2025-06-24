package com.donetick.app.ui.setup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.donetick.app.ui.theme.DoneTickTheme
import com.donetick.app.ui.webview.WebViewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Activity for server setup and configuration
 */
@AndroidEntryPoint
class SetupActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Observe navigation events
        lifecycleScope.launch {
            viewModel.navigationEvent.collect { event ->
                when (event) {
                    is SetupNavigationEvent.NavigateToWebView -> {
                        navigateToWebView(event.serverUrl)
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
                
                SetupScreen(
                    uiState = uiState,
                    onUrlChanged = viewModel::updateUrl,
                    onConnectClicked = viewModel::connectToServer
                )
            }
        }
    }

    /**
     * Navigates to WebView activity
     */
    private fun navigateToWebView(serverUrl: String) {
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra(WebViewActivity.EXTRA_SERVER_URL, serverUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
