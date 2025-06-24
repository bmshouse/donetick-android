package com.donetick.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.donetick.app.domain.usecase.GetServerConfigUseCase
import com.donetick.app.ui.setup.SetupActivity
import com.donetick.app.ui.webview.WebViewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main activity that handles initial routing based on server configuration
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var getServerConfigUseCase: GetServerConfigUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check server configuration and route accordingly
        lifecycleScope.launch {
            try {
                val config = getServerConfigUseCase.getCurrentConfig()
                
                if (config.isConfigured && config.url.isNotEmpty()) {
                    // Server is configured, go to WebView
                    navigateToWebView()
                } else {
                    // No server configured, go to setup
                    navigateToSetup()
                }
            } catch (e: Exception) {
                // Error loading config, go to setup
                navigateToSetup()
            }
        }
    }

    /**
     * Navigates to WebView activity
     */
    private fun navigateToWebView() {
        val intent = Intent(this, WebViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
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
}
