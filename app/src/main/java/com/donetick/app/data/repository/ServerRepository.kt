package com.donetick.app.data.repository

import com.donetick.app.data.model.ServerConfig
import com.donetick.app.data.preferences.SecurePreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing server configuration and connectivity
 */
@Singleton
class ServerRepository @Inject constructor(
    private val preferencesManager: SecurePreferencesManager
) {
    /**
     * Flow of server configuration changes
     */
    val serverConfigFlow: Flow<ServerConfig> = preferencesManager.serverConfigFlow

    /**
     * Gets current server configuration
     */
    fun getServerConfig(): ServerConfig = preferencesManager.getServerConfig()

    /**
     * Saves server configuration
     */
    fun saveServerConfig(config: ServerConfig) {
        preferencesManager.saveServerConfig(config)
    }

    /**
     * Updates server URL and validates it
     */
    suspend fun updateServerUrl(url: String): Result<ServerConfig> = withContext(Dispatchers.IO) {
        try {
            val config = ServerConfig(url = url)

            // Validate URL format
            if (!config.isValidUrl()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid URL format. Please enter a valid URL (e.g., https://your-server.com)")
                )
            }

            // Test connectivity with timeout
            val isReachable = testServerConnectivity(config.getNormalizedUrl())
            if (!isReachable) {
                return@withContext Result.failure(
                    ConnectException("Unable to connect to server. Please check if the server is running and accessible.")
                )
            }

            // Save configuration
            val validatedConfig = config.copy(
                isConfigured = true,
                lastValidated = System.currentTimeMillis()
            )
            preferencesManager.saveServerConfig(validatedConfig)

            Result.success(validatedConfig)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Tests server connectivity
     */
    suspend fun testServerConnectivity(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = true
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            // Accept any 2xx or 3xx response, or 401/403 (server exists but requires auth)
            responseCode in 200..399 || responseCode in listOf(401, 403)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates current server configuration
     */
    suspend fun validateCurrentServer(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val config = getServerConfig()
            if (!config.isConfigured || config.url.isEmpty()) {
                return@withContext Result.failure(Exception("No server configured"))
            }

            val isReachable = testServerConnectivity(config.getNormalizedUrl())
            if (isReachable) {
                preferencesManager.updateLastValidated()
            }
            
            Result.success(isReachable)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clears server configuration
     */
    fun clearServerConfig() {
        preferencesManager.clearServerConfig()
    }

    /**
     * Checks if server is configured
     */
    fun isServerConfigured(): Boolean = preferencesManager.isServerConfigured()
}
