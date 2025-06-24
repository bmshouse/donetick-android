package com.donetick.app.domain.usecase

import com.donetick.app.data.model.ServerConfig
import com.donetick.app.data.repository.ServerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

/**
 * Unit tests for ValidateServerUrlUseCase
 */
@RunWith(MockitoJUnitRunner::class)
class ValidateServerUrlUseCaseTest {

    @Mock
    private lateinit var serverRepository: ServerRepository

    private lateinit var useCase: ValidateServerUrlUseCase

    @Before
    fun setup() {
        useCase = ValidateServerUrlUseCase(serverRepository)
    }

    @Test
    fun `invoke returns failure for blank URL`() = runTest {
        val result = useCase("")
        assertTrue(result.isFailure)
        assertEquals("URL cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke returns failure for whitespace URL`() = runTest {
        val result = useCase("   ")
        assertTrue(result.isFailure)
        assertEquals("URL cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke calls repository with trimmed URL`() = runTest {
        val url = "  https://example.com  "
        val expectedConfig = ServerConfig(
            url = "https://example.com",
            isConfigured = true,
            lastValidated = System.currentTimeMillis()
        )
        
        whenever(serverRepository.updateServerUrl("https://example.com"))
            .thenReturn(Result.success(expectedConfig))

        val result = useCase(url)
        
        assertTrue(result.isSuccess)
        assertEquals(expectedConfig, result.getOrNull())
    }

    @Test
    fun `invoke propagates repository errors`() = runTest {
        val url = "https://example.com"
        val error = Exception("Connection failed")
        
        whenever(serverRepository.updateServerUrl(url))
            .thenReturn(Result.failure(error))

        val result = useCase(url)
        
        assertTrue(result.isFailure)
        assertEquals("Failed to connect to server: Connection failed", result.exceptionOrNull()?.message)
    }
}
