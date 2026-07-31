package org.chaosorderx.donetick.domain.usecase

import org.chaosorderx.donetick.data.model.ServerConfig
import org.chaosorderx.donetick.data.repository.ServerRepository
import javax.inject.Inject

/**
 * Use case for managing server configuration operations
 */
class ManageServerConfigUseCase @Inject constructor(
    private val serverRepository: ServerRepository
) {
    /**
     * Updates server URL with validation
     * @param newUrl The new server URL
     * @return Result containing the updated ServerConfig or error
     */
    suspend fun updateServerUrl(newUrl: String): Result<ServerConfig> {
        if (newUrl.isBlank()) {
            return Result.failure(Exception("URL cannot be empty"))
        }

        return try {
            serverRepository.updateServerUrl(newUrl.trim())
        } catch (e: Exception) {
            Result.failure(Exception("Failed to update server URL: ${e.message}"))
        }
    }

    /**
     * Disconnects from current server (clears configuration)
     */
    fun disconnectFromServer() {
        serverRepository.clearServerConfig()
    }

    /**
     * Saves server configuration
     * @param config The server configuration to save
     */
    fun saveServerConfig(config: ServerConfig) {
        serverRepository.saveServerConfig(config)
    }
}
