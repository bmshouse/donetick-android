package com.donetick.app.data.model

/**
 * Data model representing server configuration
 */
data class ServerConfig(
    val url: String,
    val isConfigured: Boolean = false,
    val lastValidated: Long = 0L
) {
    /**
     * Returns a normalized URL with proper protocol and trailing slash handling
     */
    fun getNormalizedUrl(): String {
        var normalizedUrl = url.trim()
        
        // Add https:// if no protocol is specified
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            normalizedUrl = "https://$normalizedUrl"
        }
        
        // Remove trailing slash
        if (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.dropLast(1)
        }
        
        return normalizedUrl
    }
    
    /**
     * Checks if the URL appears to be valid format
     */
    fun isValidUrl(): Boolean {
        val normalizedUrl = getNormalizedUrl()
        return try {
            val url = java.net.URL(normalizedUrl)
            url.protocol in listOf("http", "https") &&
            url.host.isNotEmpty() &&
            (url.host.contains(".") || url.host == "localhost")
        } catch (e: Exception) {
            false
        }
    }
    
    companion object {
        fun empty() = ServerConfig(url = "", isConfigured = false)
    }
}
