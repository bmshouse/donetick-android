package com.donetick.app.domain.usecase

import com.donetick.app.data.model.ServerConfig
import com.donetick.app.data.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ManageServerConfigUseCaseTest {

    private lateinit var manageServerConfigUseCase: ManageServerConfigUseCase
    private lateinit var mockServerRepository: ServerRepository

    @Before
    fun setup() {
        mockServerRepository = mockk(relaxed = true)
        manageServerConfigUseCase = ManageServerConfigUseCase(mockServerRepository)
    }

    @Test
    fun `updateServerUrl returns failure for blank URL`() = runTest {
        // When
        val result = manageServerConfigUseCase.updateServerUrl("")

        // Then
        assertTrue(result.isFailure)
        assertEquals("URL cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `updateServerUrl returns failure for whitespace URL`() = runTest {
        // When
        val result = manageServerConfigUseCase.updateServerUrl("   ")

        // Then
        assertTrue(result.isFailure)
        assertEquals("URL cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `updateServerUrl trims URL and delegates to repository`() = runTest {
        // Given
        val urlWithSpaces = "  https://example.com  "
        val trimmedUrl = "https://example.com"
        val expectedConfig = ServerConfig(url = trimmedUrl, isConfigured = true)
        
        coEvery { mockServerRepository.updateServerUrl(trimmedUrl) } returns Result.success(expectedConfig)

        // When
        val result = manageServerConfigUseCase.updateServerUrl(urlWithSpaces)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedConfig, result.getOrNull())
        coVerify { mockServerRepository.updateServerUrl(trimmedUrl) }
    }

    @Test
    fun `updateServerUrl returns success when repository succeeds`() = runTest {
        // Given
        val url = "https://example.com"
        val expectedConfig = ServerConfig(url = url, isConfigured = true, lastValidated = 123456789L)
        
        coEvery { mockServerRepository.updateServerUrl(url) } returns Result.success(expectedConfig)

        // When
        val result = manageServerConfigUseCase.updateServerUrl(url)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedConfig, result.getOrNull())
    }

    @Test
    fun `updateServerUrl returns failure when repository fails`() = runTest {
        // Given
        val url = "https://example.com"
        val repositoryError = Exception("Connection failed")

        coEvery { mockServerRepository.updateServerUrl(url) } returns Result.failure(repositoryError)

        // When
        val result = manageServerConfigUseCase.updateServerUrl(url)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        // The use case just returns the repository result directly, so the error message should be the original
        assertEquals("Connection failed", exception?.message)
    }

    @Test
    fun `updateServerUrl handles repository exception`() = runTest {
        // Given
        val url = "https://example.com"
        val repositoryException = RuntimeException("Unexpected error")
        
        coEvery { mockServerRepository.updateServerUrl(url) } throws repositoryException

        // When
        val result = manageServerConfigUseCase.updateServerUrl(url)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception?.message?.contains("Failed to update server URL") == true)
        assertTrue(exception?.message?.contains("Unexpected error") == true)
    }

    @Test
    fun `disconnectFromServer delegates to repository`() {
        // When
        manageServerConfigUseCase.disconnectFromServer()

        // Then
        verify { mockServerRepository.clearServerConfig() }
    }

    @Test
    fun `saveServerConfig delegates to repository`() {
        // Given
        val config = ServerConfig(
            url = "https://example.com",
            isConfigured = true,
            lastValidated = 123456789L
        )

        // When
        manageServerConfigUseCase.saveServerConfig(config)

        // Then
        verify { mockServerRepository.saveServerConfig(config) }
    }

    @Test
    fun `saveServerConfig handles different configurations`() {
        // Test various server configurations
        val testConfigs = listOf(
            ServerConfig.empty(),
            ServerConfig(url = "http://localhost:8080", isConfigured = false),
            ServerConfig(url = "https://my-server.com", isConfigured = true, lastValidated = System.currentTimeMillis())
        )

        testConfigs.forEach { config ->
            // When
            manageServerConfigUseCase.saveServerConfig(config)

            // Then
            verify { mockServerRepository.saveServerConfig(config) }
        }
    }

    @Test
    fun `updateServerUrl with valid URLs of different formats`() = runTest {
        val testUrls = listOf(
            "https://example.com",
            "http://localhost:8080",
            "https://my-server.org:3000",
            "https://192.168.1.100:8080"
        )

        testUrls.forEach { url ->
            // Given
            val expectedConfig = ServerConfig(url = url, isConfigured = true)
            coEvery { mockServerRepository.updateServerUrl(url) } returns Result.success(expectedConfig)

            // When
            val result = manageServerConfigUseCase.updateServerUrl(url)

            // Then
            assertTrue("Failed for URL: $url", result.isSuccess)
            assertEquals(expectedConfig, result.getOrNull())
        }
    }

    @Test
    fun `multiple operations work correctly together`() = runTest {
        // Given
        val config1 = ServerConfig(url = "https://server1.com", isConfigured = true)
        val config2 = ServerConfig(url = "https://server2.com", isConfigured = true)
        
        coEvery { mockServerRepository.updateServerUrl("https://server1.com") } returns Result.success(config1)
        coEvery { mockServerRepository.updateServerUrl("https://server2.com") } returns Result.success(config2)

        // When - perform multiple operations
        val result1 = manageServerConfigUseCase.updateServerUrl("https://server1.com")
        manageServerConfigUseCase.saveServerConfig(config1)
        val result2 = manageServerConfigUseCase.updateServerUrl("https://server2.com")
        manageServerConfigUseCase.disconnectFromServer()

        // Then
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        verify { mockServerRepository.saveServerConfig(config1) }
        verify { mockServerRepository.clearServerConfig() }
    }
}
