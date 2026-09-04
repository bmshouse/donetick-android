package org.chaosorderx.donetick.data.remote

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal HTTP GET wrapper over [HttpURLConnection].
 *
 * The native API surface is a single authenticated GET (the sync endpoint, or the legacy
 * chores endpoint as a fallback), so this deliberately avoids pulling in OkHttp. Token
 * refresh is the WebView's responsibility, not this client's.
 */
@Singleton
class HttpClient @Inject constructor() {

    data class Response(val code: Int, val body: String)

    /** Test seam — overridden in unit tests to return a stubbed connection. */
    internal var openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }

    /**
     * Performs `GET [url]` with a bearer token. Blocking — call from a background dispatcher.
     * @throws java.io.IOException on transport failure.
     */
    fun get(
        url: String,
        bearerToken: String,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 20_000,
    ): Response {
        val conn = openConnection(URL(url)).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(code, body)
        } finally {
            conn.disconnect()
        }
    }
}
