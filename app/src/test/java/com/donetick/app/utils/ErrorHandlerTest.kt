package com.donetick.app.utils

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Unit tests for ErrorHandler utility
 */
@RunWith(MockitoJUnitRunner::class)
class ErrorHandlerTest {

    @Mock
    private lateinit var context: Context

    @Test
    fun `getUrlValidationError returns null for valid URLs`() {
        val validUrls = listOf(
            "https://example.com",
            "http://localhost:8080",
            "example.com"
        )

        validUrls.forEach { url ->
            assertNull("Should be valid: $url", ErrorHandler.getUrlValidationError(url))
        }
    }

    @Test
    fun `getUrlValidationError returns error for invalid URLs`() {
        assertEquals(
            "Please enter a server URL",
            ErrorHandler.getUrlValidationError("")
        )
        
        assertEquals(
            "Please enter a server URL",
            ErrorHandler.getUrlValidationError("   ")
        )
        
        assertEquals(
            "Please enter a valid URL (e.g., https://your-server.com)",
            ErrorHandler.getUrlValidationError("invalid-url")
        )
        
        assertEquals(
            "URL cannot contain spaces",
            ErrorHandler.getUrlValidationError("https://example .com")
        )
    }

    @Test
    fun `isRecoverableError returns true for network errors`() {
        assertTrue(ErrorHandler.isRecoverableError(SocketTimeoutException()))
        assertTrue(ErrorHandler.isRecoverableError(ConnectException()))
        assertTrue(ErrorHandler.isRecoverableError(UnknownHostException()))
    }

    @Test
    fun `isRecoverableError returns false for non-network errors`() {
        assertFalse(ErrorHandler.isRecoverableError(IllegalArgumentException()))
        assertFalse(ErrorHandler.isRecoverableError(RuntimeException()))
        assertFalse(ErrorHandler.isRecoverableError(SSLException("SSL Error")))
    }

    @Test
    fun `getErrorMessage returns appropriate messages for different exceptions`() {
        assertEquals(
            "Server not found. Please check the URL and your internet connection.",
            ErrorHandler.getErrorMessage(context, UnknownHostException())
        )
        
        assertEquals(
            "Unable to connect to server. Please check if the server is running and accessible.",
            ErrorHandler.getErrorMessage(context, ConnectException())
        )
        
        assertEquals(
            "Connection timeout. The server is taking too long to respond. Please try again.",
            ErrorHandler.getErrorMessage(context, SocketTimeoutException())
        )
        
        assertEquals(
            "Secure connection failed. Please check if the server supports HTTPS.",
            ErrorHandler.getErrorMessage(context, SSLException("SSL Error"))
        )
    }
}
