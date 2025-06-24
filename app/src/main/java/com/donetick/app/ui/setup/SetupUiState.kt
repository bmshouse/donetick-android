package com.donetick.app.ui.setup

/**
 * UI state for the setup screen
 */
data class SetupUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val url: String = "",
    val isUrlValid: Boolean = false
) {
    companion object {
        fun initial() = SetupUiState()
    }
}
