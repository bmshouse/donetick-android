package com.donetick.app.domain.usecase

import com.donetick.app.data.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CheckServerConnectivityUseCaseTest {

    private lateinit var checkServerConnectivityUseCase: CheckServerConnectivityUseCase
    private lateinit var mockServerRepository: ServerRepository

    @Before
    fun setup() {
        mockServerRepository = mockk()
        checkServerConnectivityUseCase = CheckServerConnectivityUseCase(mockServerRepository)
    }

    @Test
    fun `testUrl returns true when server is reachable`() = runTest {
        // Given
        val testUrl = "https://example.com"
        coEvery { mockServerRepository.testServerConnectivity(testUrl) } returns true

        // When
        val result = checkServerConnectivityUseCase.testUrl(testUrl)

        // Then
        assertTrue(result)
    }

    @Test
    fun `testUrl returns false when server is not reachable`() = runTest {
        // Given
        val testUrl = "https://unreachable.com"
        coEvery { mockServerRepository.testServerConnectivity(testUrl) } returns false

        // When
        val result = checkServerConnectivityUseCase.testUrl(testUrl)

        // Then
        assertFalse(result)
    }

    @Test
    fun `testUrl returns false when repository throws exception`() = runTest {
        // Given
        val testUrl = "https://example.com"
        coEvery { mockServerRepository.testServerConnectivity(testUrl) } throws RuntimeException("Network error")

        // When
        val result = checkServerConnectivityUseCase.testUrl(testUrl)

        // Then
        assertFalse(result)
    }

    @Test
    fun `validateCurrentServer returns success when server is reachable`() = runTest {
        // Given
        coEvery { mockServerRepository.validateCurrentServer() } returns Result.success(true)

        // When
        val result = checkServerConnectivityUseCase.validateCurrentServer()

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `validateCurrentServer returns success with false when server is not reachable`() = runTest {
        // Given
        coEvery { mockServerRepository.validateCurrentServer() } returns Result.success(false)

        // When
        val result = checkServerConnectivityUseCase.validateCurrentServer()

        // Then
        assertTrue(result.isSuccess)
        assertFalse(result.getOrNull() == true)
    }

    @Test
    fun `validateCurrentServer returns failure when repository returns error`() = runTest {
        // Given
        val errorMessage = "No server configured"
        coEvery { mockServerRepository.validateCurrentServer() } returns Result.failure(Exception(errorMessage))

        // When
        val result = checkServerConnectivityUseCase.validateCurrentServer()

        // Then
        assertTrue(result.isFailure)
        assertEquals(errorMessage, result.exceptionOrNull()?.message)
    }

    @Test
    fun `testUrl handles empty URL gracefully`() = runTest {
        // Given
        val emptyUrl = ""
        coEvery { mockServerRepository.testServerConnectivity(emptyUrl) } throws IllegalArgumentException("Empty URL")

        // When
        val result = checkServerConnectivityUseCase.testUrl(emptyUrl)

        // Then
        assertFalse(result)
    }

    @Test
    fun `testUrl handles malformed URL gracefully`() = runTest {
        // Given
        val malformedUrl = "not-a-url"
        coEvery { mockServerRepository.testServerConnectivity(malformedUrl) } throws Exception("Malformed URL")

        // When
        val result = checkServerConnectivityUseCase.testUrl(malformedUrl)

        // Then
        assertFalse(result)
    }
}
