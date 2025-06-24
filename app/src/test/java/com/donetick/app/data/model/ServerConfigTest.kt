package com.donetick.app.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ServerConfig data model
 */
class ServerConfigTest {

    @Test
    fun `getNormalizedUrl adds https prefix when missing`() {
        val config = ServerConfig(url = "example.com")
        assertEquals("https://example.com", config.getNormalizedUrl())
    }

    @Test
    fun `getNormalizedUrl preserves existing https prefix`() {
        val config = ServerConfig(url = "https://example.com")
        assertEquals("https://example.com", config.getNormalizedUrl())
    }

    @Test
    fun `getNormalizedUrl preserves http prefix`() {
        val config = ServerConfig(url = "http://example.com")
        assertEquals("http://example.com", config.getNormalizedUrl())
    }

    @Test
    fun `getNormalizedUrl removes trailing slash`() {
        val config = ServerConfig(url = "https://example.com/")
        assertEquals("https://example.com", config.getNormalizedUrl())
    }

    @Test
    fun `getNormalizedUrl handles multiple trailing slashes`() {
        val config = ServerConfig(url = "https://example.com///")
        assertEquals("https://example.com//", config.getNormalizedUrl())
    }

    @Test
    fun `isValidUrl returns true for valid URLs`() {
        val validUrls = listOf(
            "https://example.com",
            "http://localhost:8080",
            "https://sub.domain.com",
            "http://192.168.1.1:3000"
        )

        validUrls.forEach { url ->
            val config = ServerConfig(url = url)
            assertTrue("URL should be valid: $url", config.isValidUrl())
        }
    }

    @Test
    fun `isValidUrl returns false for invalid URLs`() {
        val invalidUrls = listOf(
            "",
            "not-a-url",
            "ftp://example.com",
            "https://",
            "http://"
        )

        invalidUrls.forEach { url ->
            val config = ServerConfig(url = url)
            assertFalse("URL should be invalid: $url", config.isValidUrl())
        }
    }

    @Test
    fun `empty creates default empty config`() {
        val config = ServerConfig.empty()
        assertEquals("", config.url)
        assertFalse(config.isConfigured)
        assertEquals(0L, config.lastValidated)
    }
}
