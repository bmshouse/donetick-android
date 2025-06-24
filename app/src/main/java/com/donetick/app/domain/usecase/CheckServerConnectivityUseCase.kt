package com.donetick.app.domain.usecase

import com.donetick.app.data.repository.ServerRepository
import javax.inject.Inject

/**
 * Use case for checking server connectivity
 */
class CheckServerConnectivityUseCase @Inject constructor(
    private val serverRepository: ServerRepository
) {
    /**
     * Tests connectivity to a specific server URL
     * @param url The server URL to test
     * @return true if server is reachable, false otherwise
     */
    suspend fun testUrl(url: String): Boolean {
        return try {
            serverRepository.testServerConnectivity(url)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates connectivity to the currently configured server
     * @return Result containing true if server is reachable, or error
     */
    suspend fun validateCurrentServer(): Result<Boolean> {
        return serverRepository.validateCurrentServer()
    }
}
