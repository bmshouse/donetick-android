package com.donetick.app.utils

import android.content.Context
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Utility class for handling and formatting errors with user-friendly messages
 */
object ErrorHandler {

    /**
     * Converts exceptions to user-friendly error messages
     */
    fun getErrorMessage(context: Context, throwable: Throwable): String {
        return when (throwable) {
            is UnknownHostException -> {
                "Server not found. Please check the URL and your internet connection."
            }
            is ConnectException -> {
                "Unable to connect to server. Please check if the server is running and accessible."
            }
            is SocketTimeoutException -> {
                "Connection timeout. The server is taking too long to respond. Please try again."
            }
            is SSLException -> {
                "Secure connection failed. Please check if the server supports HTTPS."
            }
            is java.net.MalformedURLException -> {
                "Invalid URL format. Please enter a valid server URL."
            }
            else -> {
                // Check if it's a network connectivity issue
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    "No internet connection. Please check your network settings."
                } else {
                    // Generic error message
                    throwable.message?.let { message ->
                        if (message.isNotBlank()) {
                            "Error: $message"
                        } else {
                            "An unexpected error occurred. Please try again."
                        }
                    } ?: "An unexpected error occurred. Please try again."
                }
            }
        }
    }

    /**
     * Checks if an error is recoverable (user can retry)
     */
    fun isRecoverableError(throwable: Throwable): Boolean {
        return when (throwable) {
            is SocketTimeoutException,
            is ConnectException,
            is UnknownHostException -> true
            else -> false
        }
    }

    /**
     * Gets a specific error message for URL validation
     */
    fun getUrlValidationError(url: String): String? {
        return when {
            url.isBlank() -> "Please enter a server URL"
            url.contains(" ") -> "URL cannot contain spaces"
            else -> {
                // Try to validate as a proper URL
                try {
                    val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        "https://$url"
                    } else {
                        url
                    }
                    val javaUrl = java.net.URL(normalizedUrl)
                    if (javaUrl.host.isEmpty() || (!javaUrl.host.contains(".") && javaUrl.host != "localhost")) {
                        "Please enter a valid URL (e.g., https://your-server.com)"
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    "Please enter a valid URL (e.g., https://your-server.com)"
                }
            }
        }
    }
}
