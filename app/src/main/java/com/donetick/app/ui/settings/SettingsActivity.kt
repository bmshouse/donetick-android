package com.donetick.app.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.donetick.app.ui.setup.SetupActivity
import com.donetick.app.ui.theme.DoneTickTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Activity for managing application settings
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Observe navigation events
        lifecycleScope.launch {
            viewModel.navigationEvent.collect { event ->
                when (event) {
                    is SettingsNavigationEvent.NavigateToSetup -> {
                        navigateToSetup()
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
                
                SettingsScreen(
                    uiState = uiState,
                    onBackClick = { finish() },
                    onChangeServerClick = viewModel::changeServer,
                    onDisconnectClick = viewModel::disconnectFromServer
                )
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
}
