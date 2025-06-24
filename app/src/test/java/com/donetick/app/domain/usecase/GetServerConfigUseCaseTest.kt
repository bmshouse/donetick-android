package com.donetick.app.domain.usecase

import com.donetick.app.data.model.ServerConfig
import com.donetick.app.data.repository.ServerRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GetServerConfigUseCaseTest {

    private lateinit var getServerConfigUseCase: GetServerConfigUseCase
    private lateinit var mockServerRepository: ServerRepository

    @Before
    fun setup() {
        mockServerRepository = mockk()
        getServerConfigUseCase = GetServerConfigUseCase(mockServerRepository)
    }

    @Test
    fun `getCurrentConfig returns server config from repository`() {
        // Given
        val expectedConfig = ServerConfig(
            url = "https://example.com",
            isConfigured = true,
            lastValidated = 123456789L
        )
        every { mockServerRepository.getServerConfig() } returns expectedConfig

        // When
        val result = getServerConfigUseCase.getCurrentConfig()

        // Then
        assertEquals(expectedConfig, result)
    }

    @Test
    fun `getCurrentConfig returns empty config when not configured`() {
        // Given
        val emptyConfig = ServerConfig.empty()
        every { mockServerRepository.getServerConfig() } returns emptyConfig

        // When
        val result = getServerConfigUseCase.getCurrentConfig()

        // Then
        assertEquals(emptyConfig, result)
        assertEquals("", result.url)
        assertFalse(result.isConfigured)
        assertEquals(0L, result.lastValidated)
    }

    @Test
    fun `getConfigFlow returns flow from repository`() {
        // Given
        val config = ServerConfig(
            url = "https://example.com",
            isConfigured = true,
            lastValidated = 123456789L
        )
        val expectedFlow = flowOf(config)
        every { mockServerRepository.serverConfigFlow } returns expectedFlow

        // When
        val result = getServerConfigUseCase.getConfigFlow()

        // Then
        assertEquals(expectedFlow, result)
    }

    @Test
    fun `isServerConfigured returns true when server is configured`() {
        // Given
        every { mockServerRepository.isServerConfigured() } returns true

        // When
        val result = getServerConfigUseCase.isServerConfigured()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isServerConfigured returns false when server is not configured`() {
        // Given
        every { mockServerRepository.isServerConfigured() } returns false

        // When
        val result = getServerConfigUseCase.isServerConfigured()

        // Then
        assertFalse(result)
    }

    @Test
    fun `getCurrentConfig handles different server configurations`() {
        // Test various server configurations
        val testConfigs = listOf(
            ServerConfig(url = "http://localhost:8080", isConfigured = true, lastValidated = 0L),
            ServerConfig(url = "https://my-server.com", isConfigured = true, lastValidated = System.currentTimeMillis()),
            ServerConfig(url = "https://test.example.org:3000", isConfigured = false, lastValidated = 123L)
        )

        testConfigs.forEach { config ->
            // Given
            every { mockServerRepository.getServerConfig() } returns config

            // When
            val result = getServerConfigUseCase.getCurrentConfig()

            // Then
            assertEquals(config, result)
            assertEquals(config.url, result.url)
            assertEquals(config.isConfigured, result.isConfigured)
            assertEquals(config.lastValidated, result.lastValidated)
        }
    }

    @Test
    fun `use case methods delegate to repository correctly`() {
        // Given
        val config = ServerConfig(url = "https://test.com", isConfigured = true)
        val configFlow = flowOf(config)
        
        every { mockServerRepository.getServerConfig() } returns config
        every { mockServerRepository.serverConfigFlow } returns configFlow
        every { mockServerRepository.isServerConfigured() } returns true

        // When & Then
        assertEquals(config, getServerConfigUseCase.getCurrentConfig())
        assertEquals(configFlow, getServerConfigUseCase.getConfigFlow())
        assertTrue(getServerConfigUseCase.isServerConfigured())
    }
}
