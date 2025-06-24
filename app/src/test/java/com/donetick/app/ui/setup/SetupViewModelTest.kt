package com.donetick.app.ui.setup

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.donetick.app.data.model.ServerConfig
import com.donetick.app.domain.usecase.ValidateServerUrlUseCase
import com.donetick.app.utils.ErrorHandler
import com.donetick.app.utils.NetworkUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var setupViewModel: SetupViewModel
    private lateinit var mockValidateServerUrlUseCase: ValidateServerUrlUseCase
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockValidateServerUrlUseCase = mockk()
        mockContext = mockk(relaxed = true)
        
        // Mock static objects
        mockkObject(ErrorHandler)
        mockkObject(NetworkUtils)
        
        setupViewModel = SetupViewModel(mockValidateServerUrlUseCase, mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ErrorHandler)
        unmockkObject(NetworkUtils)
    }

    @Test
    fun `initial state is correct`() {
        val initialState = setupViewModel.uiState.value
        
        assertEquals(SetupUiState.initial(), initialState)
        assertFalse(initialState.isLoading)
        assertNull(initialState.errorMessage)
        assertEquals("", initialState.url)
        assertFalse(initialState.isUrlValid)
    }

    @Test
    fun `updateUrl updates state correctly with valid URL`() {
        val testUrl = "https://example.com"
        
        setupViewModel.updateUrl(testUrl)
        
        val state = setupViewModel.uiState.value
        assertEquals(testUrl, state.url)
        assertTrue(state.isUrlValid)
        assertNull(state.errorMessage)
    }

    @Test
    fun `updateUrl updates state correctly with empty URL`() {
        setupViewModel.updateUrl("")
        
        val state = setupViewModel.uiState.value
        assertEquals("", state.url)
        assertFalse(state.isUrlValid)
        assertNull(state.errorMessage)
    }

    @Test
    fun `updateUrl clears previous error message`() {
        // First set an error
        setupViewModel.updateUrl("")
        setupViewModel.connectToServer()
        
        // Then update URL
        setupViewModel.updateUrl("https://example.com")
        
        val state = setupViewModel.uiState.value
        assertNull(state.errorMessage)
    }

    @Test
    fun `connectToServer shows validation error for invalid URL`() {
        val invalidUrl = "invalid-url"
        val errorMessage = "Invalid URL format"
        
        every { ErrorHandler.getUrlValidationError(invalidUrl) } returns errorMessage
        
        setupViewModel.updateUrl(invalidUrl)
        setupViewModel.connectToServer()
        
        val state = setupViewModel.uiState.value
        assertEquals(errorMessage, state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `connectToServer shows network error when no internet`() {
        val validUrl = "https://example.com"
        
        every { ErrorHandler.getUrlValidationError(validUrl) } returns null
        every { NetworkUtils.isNetworkAvailable(mockContext) } returns false
        
        setupViewModel.updateUrl(validUrl)
        setupViewModel.connectToServer()
        
        val state = setupViewModel.uiState.value
        assertEquals("No internet connection. Please check your network settings.", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `connectToServer successful validation navigates to WebView`() = runTest {
        val validUrl = "https://example.com"
        val serverConfig = ServerConfig(url = validUrl, isConfigured = true)
        
        every { ErrorHandler.getUrlValidationError(validUrl) } returns null
        every { NetworkUtils.isNetworkAvailable(mockContext) } returns true
        coEvery { mockValidateServerUrlUseCase(validUrl) } returns Result.success(serverConfig)
        
        setupViewModel.updateUrl(validUrl)
        setupViewModel.connectToServer()
        
        advanceUntilIdle()
        
        val state = setupViewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        
        val navigationEvent = setupViewModel.navigationEvent.value
        assertTrue(navigationEvent is SetupNavigationEvent.NavigateToWebView)
        assertEquals(serverConfig.getNormalizedUrl(), (navigationEvent as SetupNavigationEvent.NavigateToWebView).serverUrl)
    }

    @Test
    fun `connectToServer failed validation shows error message`() = runTest {
        val validUrl = "https://example.com"
        val errorException = Exception("Connection failed")
        val errorMessage = "Failed to connect to server"
        
        every { ErrorHandler.getUrlValidationError(validUrl) } returns null
        every { NetworkUtils.isNetworkAvailable(mockContext) } returns true
        coEvery { mockValidateServerUrlUseCase(validUrl) } returns Result.failure(errorException)
        every { ErrorHandler.getErrorMessage(mockContext, errorException) } returns errorMessage
        
        setupViewModel.updateUrl(validUrl)
        setupViewModel.connectToServer()
        
        advanceUntilIdle()
        
        val state = setupViewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(errorMessage, state.errorMessage)
        
        val navigationEvent = setupViewModel.navigationEvent.value
        assertNull(navigationEvent)
    }

    @Test
    fun `connectToServer sets loading state during validation`() = runTest {
        val validUrl = "https://example.com"
        val serverConfig = ServerConfig(url = validUrl, isConfigured = true)
        
        every { ErrorHandler.getUrlValidationError(validUrl) } returns null
        every { NetworkUtils.isNetworkAvailable(mockContext) } returns true
        coEvery { mockValidateServerUrlUseCase(validUrl) } returns Result.success(serverConfig)
        
        setupViewModel.updateUrl(validUrl)
        setupViewModel.connectToServer()
        
        // Check loading state is set immediately
        val loadingState = setupViewModel.uiState.value
        assertTrue(loadingState.isLoading)
        assertNull(loadingState.errorMessage)
        
        advanceUntilIdle()
        
        // Check loading state is cleared after completion
        val finalState = setupViewModel.uiState.value
        assertFalse(finalState.isLoading)
    }

    @Test
    fun `connectToServer handles unexpected exceptions`() = runTest {
        val validUrl = "https://example.com"
        val exception = RuntimeException("Unexpected error")
        val errorMessage = "Unexpected error occurred"
        
        every { ErrorHandler.getUrlValidationError(validUrl) } returns null
        every { NetworkUtils.isNetworkAvailable(mockContext) } returns true
        coEvery { mockValidateServerUrlUseCase(validUrl) } throws exception
        every { ErrorHandler.getErrorMessage(mockContext, exception) } returns errorMessage
        
        setupViewModel.updateUrl(validUrl)
        setupViewModel.connectToServer()
        
        advanceUntilIdle()
        
        val state = setupViewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(errorMessage, state.errorMessage)
    }

    @Test
    fun `clearNavigationEvent clears navigation event`() = runTest {
        val validUrl = "https://example.com"
        val serverConfig = ServerConfig(url = validUrl, isConfigured = true)
        
        every { ErrorHandler.getUrlValidationError(validUrl) } returns null
        every { NetworkUtils.isNetworkAvailable(mockContext) } returns true
        coEvery { mockValidateServerUrlUseCase(validUrl) } returns Result.success(serverConfig)
        
        setupViewModel.updateUrl(validUrl)
        setupViewModel.connectToServer()
        advanceUntilIdle()
        
        // Verify navigation event is set
        assertNotNull(setupViewModel.navigationEvent.value)
        
        // Clear navigation event
        setupViewModel.clearNavigationEvent()
        
        // Verify navigation event is cleared
        assertNull(setupViewModel.navigationEvent.value)
    }

    @Test
    fun `clearError clears error message`() {
        val errorMessage = "Test error"
        
        // Set error by trying to connect with invalid URL
        every { ErrorHandler.getUrlValidationError("") } returns errorMessage
        
        setupViewModel.updateUrl("")
        setupViewModel.connectToServer()
        
        // Verify error is set
        assertEquals(errorMessage, setupViewModel.uiState.value.errorMessage)
        
        // Clear error
        setupViewModel.clearError()
        
        // Verify error is cleared
        assertNull(setupViewModel.uiState.value.errorMessage)
    }

    @Test
    fun `connectToServer trims URL before validation`() = runTest {
        val urlWithSpaces = "  https://example.com  "
        val trimmedUrl = "https://example.com"
        val serverConfig = ServerConfig(url = trimmedUrl, isConfigured = true)
        
        every { ErrorHandler.getUrlValidationError(trimmedUrl) } returns null
        every { NetworkUtils.isNetworkAvailable(mockContext) } returns true
        coEvery { mockValidateServerUrlUseCase(trimmedUrl) } returns Result.success(serverConfig)
        
        setupViewModel.updateUrl(urlWithSpaces)
        setupViewModel.connectToServer()
        
        advanceUntilIdle()
        
        val navigationEvent = setupViewModel.navigationEvent.value
        assertTrue(navigationEvent is SetupNavigationEvent.NavigateToWebView)
    }
}
