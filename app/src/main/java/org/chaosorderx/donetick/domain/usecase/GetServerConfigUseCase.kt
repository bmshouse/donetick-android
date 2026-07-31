package org.chaosorderx.donetick.domain.usecase

import org.chaosorderx.donetick.data.model.ServerConfig
import org.chaosorderx.donetick.data.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving server configuration
 */
class GetServerConfigUseCase @Inject constructor(
    private val serverRepository: ServerRepository
) {
    /**
     * Gets current server configuration
     */
    fun getCurrentConfig(): ServerConfig = serverRepository.getServerConfig()

    /**
     * Gets server configuration as a flow for reactive updates
     */
    fun getConfigFlow(): Flow<ServerConfig> = serverRepository.serverConfigFlow

    /**
     * Checks if server is configured
     */
    fun isServerConfigured(): Boolean = serverRepository.isServerConfigured()
}
