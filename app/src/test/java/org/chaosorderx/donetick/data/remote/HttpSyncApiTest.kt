package org.chaosorderx.donetick.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HttpSyncApiTest {

    private class FakeConnection(
        private val status: Int,
        private val payload: String,
        private val throwOnRead: Boolean = false,
    ) : HttpURLConnection(URL("http://example.test")) {
        override fun getResponseCode(): Int {
            if (throwOnRead) throw IOException("boom")
            return status
        }
        override fun getInputStream(): InputStream = ByteArrayInputStream(payload.toByteArray())
        override fun getErrorStream(): InputStream = ByteArrayInputStream(payload.toByteArray())
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun connect() {}
    }

    private fun api(status: Int, payload: String, throwOnRead: Boolean = false): SyncApi {
        val http = HttpClient()
        http.openConnection = { FakeConnection(status, payload, throwOnRead) }
        return HttpSyncApi(http)
    }

    private val changesBody = """
        {
          "changes": { "chores": [ {"id": 1, "name": "A"}, {"id": 2, "name": "B"} ],
                       "choreHistories": [ {"id": 9} ] },
          "deletions": { "chores": [ 5, 6 ], "choreHistories": [] },
          "cursor": 229, "hasMore": true
        }
    """.trimIndent()

    @Test
    fun `200 changes parses chores, deletions, cursor, hasMore`() = runTest {
        val r = api(200, changesBody).getChanges("http://s", "tok", 0)
        r as PageResult.Ok
        assertEquals(listOf(1, 2), r.chores.map { it.getInt("id") })
        assertEquals(setOf(5, 6), r.deletedChoreIds)
        assertEquals(229L, r.cursor)
        assertTrue(r.hasMore)
    }

    @Test
    fun `200 legacy res envelope parses as single non-paged page`() = runTest {
        val r = api(200, """{"res":[{"id":1,"name":"A"}]}""").getChoresLegacy("http://s", "tok")
        r as PageResult.Ok
        assertEquals(1, r.chores.size)
        assertEquals(0L, r.cursor)
        assertEquals(false, r.hasMore)
    }

    @Test
    fun `401 and 403 map to Unauthorized`() = runTest {
        assertTrue(api(401, "{}").getChanges("http://s", "t", 0) is PageResult.Unauthorized)
        assertTrue(api(403, "{}").getChanges("http://s", "t", 0) is PageResult.Unauthorized)
    }

    @Test
    fun `404 and 501 map to NotSupported`() = runTest {
        assertTrue(api(404, "not found").getChanges("http://s", "t", 0) is PageResult.NotSupported)
        assertTrue(api(501, "nope").getChanges("http://s", "t", 0) is PageResult.NotSupported)
    }

    @Test
    fun `500 maps to BadResponse`() = runTest {
        val r = api(500, "server error").getChanges("http://s", "t", 0)
        r as PageResult.BadResponse
        assertEquals(500, r.code)
    }

    @Test
    fun `transport failure maps to TransportError`() = runTest {
        val r = api(200, "", throwOnRead = true).getChanges("http://s", "t", 0)
        assertTrue(r is PageResult.TransportError)
    }

    @Test
    fun `malformed json body maps to BadResponse`() = runTest {
        val r = api(200, "this is not json").getChanges("http://s", "t", 0)
        assertTrue(r is PageResult.BadResponse)
    }

    @Test
    fun `empty delta still returns Ok with advanced cursor`() = runTest {
        val body = """{"changes":{"chores":[],"choreHistories":[]},"deletions":{"chores":[],"choreHistories":[]},"cursor":429,"hasMore":true}"""
        val r = api(200, body).getChanges("http://s", "t", 229)
        r as PageResult.Ok
        assertTrue(r.chores.isEmpty())
        assertEquals(429L, r.cursor)
        assertTrue(r.hasMore)
    }
}
