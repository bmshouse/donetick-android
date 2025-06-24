package com.donetick.app.domain.usecase

import com.donetick.app.data.model.ServerConfig
import com.donetick.app.data.repository.ServerRepository
import javax.inject.Inject

/**
 * Use case for validating and setting up server URL
 */
class ValidateServerUrlUseCase @Inject constructor(
    private val serverRepository: ServerRepository
) {
    /**
     * Validates and saves server URL
     * @param url The server URL to validate
     * @return Result containing the validated ServerConfig or error
     */
    suspend operator fun invoke(url: String): Result<ServerConfig> {
        if (url.isBlank()) {
            return Result.failure(Exception("URL cannot be empty"))
        }

        val trimmedUrl = url.trim()
        
        // Basic URL format validation
        val config = ServerConfig(url = trimmedUrl)
        if (!config.isValidUrl()) {
            return Result.failure(Exception("Invalid URL format. Please enter a valid URL."))
        }

        // Test connectivity and save if successful
        return serverRepository.updateServerUrl(trimmedUrl).fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception("Failed to connect to server: ${it.message}")) }
        )
    }
}
