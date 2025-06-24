package com.donetick.app.ui.webview

/**
 * UI state for the WebView screen
 */
data class WebViewUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val serverUrl: String = "",
    val pageTitle: String = "",
    val canGoBack: Boolean = false,
    val progress: Int = 0,
    val choresData: String? = null,
    val choresList: List<ChoreItem> = emptyList()
) {
    companion object {
        fun initial() = WebViewUiState()
    }
}

/**
 * Represents a chore item from the API
 */
data class ChoreItem(
    val id: Int,
    val name: String,
    val assignedTo: Int? = null,
    val nextDueDate: String? = null,
    val isCompleted: Boolean = false,
    val frequencyType: String? = null,
    val frequency: Int = 1,
    val description: String? = null,
    val notification: Boolean = false,
    val notificationMetadata: NotificationMetadata? = null,
    val isActive: Boolean = true,
    val priority: Int = 0
)

/**
 * Represents notification metadata for a chore
 */
data class NotificationMetadata(
    val dueDate: Boolean = false
)
