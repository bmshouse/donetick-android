package com.donetick.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.donetick.app.domain.usecase.GetServerConfigUseCase
import com.donetick.app.domain.usecase.ManageServerConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the settings screen
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getServerConfigUseCase: GetServerConfigUseCase,
    private val manageServerConfigUseCase: ManageServerConfigUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState.initial())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<SettingsNavigationEvent?>(null)
    val navigationEvent: StateFlow<SettingsNavigationEvent?> = _navigationEvent.asStateFlow()

    init {
        loadCurrentConfig()
    }

    /**
     * Loads current server configuration
     */
    private fun loadCurrentConfig() {
        viewModelScope.launch {
            try {
                val config = getServerConfigUseCase.getCurrentConfig()
                _uiState.value = _uiState.value.copy(
                    currentServerUrl = config.url,
                    isServerConfigured = config.isConfigured
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to load configuration: ${e.message}"
                )
            }
        }
    }

    /**
     * Initiates server URL change
     */
    fun changeServer() {
        _navigationEvent.value = SettingsNavigationEvent.NavigateToSetup
    }

    /**
     * Disconnects from current server
     */
    fun disconnectFromServer() {
        viewModelScope.launch {
            try {
                manageServerConfigUseCase.disconnectFromServer()
                _uiState.value = _uiState.value.copy(
                    currentServerUrl = "",
                    isServerConfigured = false
                )
                _navigationEvent.value = SettingsNavigationEvent.NavigateToSetup
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to disconnect: ${e.message}"
                )
            }
        }
    }

    /**
     * Clears navigation event after handling
     */
    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }
}

/**
 * UI state for settings screen
 */
data class SettingsUiState(
    val currentServerUrl: String = "",
    val isServerConfigured: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        fun initial() = SettingsUiState()
    }
}

/**
 * Navigation events for settings screen
 */
sealed class SettingsNavigationEvent {
    object NavigateToSetup : SettingsNavigationEvent()
}
