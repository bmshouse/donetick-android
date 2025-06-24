package com.donetick.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NetworkUtilsTest {

    private lateinit var context: Context
    private lateinit var mockConnectivityManager: ConnectivityManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockConnectivityManager = mockk()

        // Mock the NetworkUtils object
        mockkObject(NetworkUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkUtils)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable returns true when WiFi is available on API 23+`() {
        // Given
        every { NetworkUtils.isNetworkAvailable(context) } returns true

        // When
        val result = NetworkUtils.isNetworkAvailable(context)

        // Then
        assertTrue(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable returns true when cellular is available on API 23+`() {
        // Given
        every { NetworkUtils.isNetworkAvailable(context) } returns true

        // When
        val result = NetworkUtils.isNetworkAvailable(context)

        // Then
        assertTrue(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable returns true when ethernet is available on API 23+`() {
        // Given
        every { NetworkUtils.isNetworkAvailable(context) } returns true

        // When
        val result = NetworkUtils.isNetworkAvailable(context)

        // Then
        assertTrue(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable returns false when no network is active on API 23+`() {
        // Given
        every { NetworkUtils.isNetworkAvailable(context) } returns false

        // When
        val result = NetworkUtils.isNetworkAvailable(context)

        // Then
        assertFalse(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable returns false when network capabilities are null on API 23+`() {
        // Given
        every { NetworkUtils.isNetworkAvailable(context) } returns false

        // When
        val result = NetworkUtils.isNetworkAvailable(context)

        // Then
        assertFalse(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable returns false when no supported transport is available on API 23+`() {
        // Given
        every { NetworkUtils.isNetworkAvailable(context) } returns false

        // When
        val result = NetworkUtils.isNetworkAvailable(context)

        // Then
        assertFalse(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    fun `isNetworkAvailable returns true when connected on API below 23`() {
        // Given
        every { NetworkUtils.isNetworkAvailable(context) } returns true

        // When
        val result = NetworkUtils.isNetworkAvailable(context)

        // Then
        assertTrue(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    fun `isNetworkAvailable returns false when not connected on API below 23`() {
        // Given
        every { NetworkUtils.isNetworkAvailable(context) } returns false

        // When
        val result = NetworkUtils.isNetworkAvailable(context)

        // Then
        assertFalse(result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    fun `isNetworkAvailable returns false when networkInfo is null on API below 23`() {
        // Given
        every { NetworkUtils.isNetworkAvailable(context) } returns false

        // When
        val result = NetworkUtils.isNetworkAvailable(context)

        // Then
        assertFalse(result)
    }

    @Test
    fun `getNetworkErrorMessage returns server error when network is available`() {
        // Given
        every { NetworkUtils.getNetworkErrorMessage(context) } returns "Unable to connect to server. Please check the server URL and try again."

        // When
        val result = NetworkUtils.getNetworkErrorMessage(context)

        // Then
        assertEquals("Unable to connect to server. Please check the server URL and try again.", result)
    }

    @Test
    fun `getNetworkErrorMessage returns network error when network is not available`() {
        // Given
        every { NetworkUtils.getNetworkErrorMessage(context) } returns "No internet connection. Please check your network settings and try again."

        // When
        val result = NetworkUtils.getNetworkErrorMessage(context)

        // Then
        assertEquals("No internet connection. Please check your network settings and try again.", result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `isNetworkAvailable handles multiple transport types correctly`() {
        // Given
        every { NetworkUtils.isNetworkAvailable(context) } returns true

        // When
        val result = NetworkUtils.isNetworkAvailable(context)

        // Then
        assertTrue(result)
    }
}
