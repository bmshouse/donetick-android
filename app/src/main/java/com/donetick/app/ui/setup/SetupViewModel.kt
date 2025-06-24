package com.donetick.app.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.donetick.app.domain.usecase.ValidateServerUrlUseCase
import com.donetick.app.utils.ErrorHandler
import com.donetick.app.utils.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the setup screen
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val validateServerUrlUseCase: ValidateServerUrlUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState.initial())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<SetupNavigationEvent?>(null)
    val navigationEvent: StateFlow<SetupNavigationEvent?> = _navigationEvent.asStateFlow()

    /**
     * Updates the URL input
     */
    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            url = url,
            errorMessage = null,
            isUrlValid = url.isNotBlank()
        )
    }

    /**
     * Validates and connects to the server
     */
    fun connectToServer() {
        val currentUrl = _uiState.value.url.trim()

        // Check for basic URL validation errors
        val validationError = ErrorHandler.getUrlValidationError(currentUrl)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError)
            return
        }

        // Check network connectivity
        if (!NetworkUtils.isNetworkAvailable(context)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "No internet connection. Please check your network settings."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val result = validateServerUrlUseCase(currentUrl)

                result.fold(
                    onSuccess = { serverConfig ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = null
                        )
                        _navigationEvent.value = SetupNavigationEvent.NavigateToWebView(
                            serverConfig.getNormalizedUrl()
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = ErrorHandler.getErrorMessage(context, error)
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = ErrorHandler.getErrorMessage(context, e)
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

    /**
     * Clears error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

/**
 * Navigation events for the setup screen
 */
sealed class SetupNavigationEvent {
    data class NavigateToWebView(val serverUrl: String) : SetupNavigationEvent()
}
