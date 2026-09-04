package org.chaosorderx.donetick.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.chaosorderx.donetick.data.model.ServerConfig
import org.chaosorderx.donetick.data.remote.PageResult
import org.chaosorderx.donetick.data.remote.SyncApi
import org.chaosorderx.donetick.domain.usecase.GetServerConfigUseCase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChoreSyncCoordinatorTest {

    private class FakeSyncApi(
        private val pages: List<PageResult>,
        private val legacy: PageResult? = null,
    ) : SyncApi {
        val sinceCalls = mutableListOf<Long>()
        var legacyCalls = 0
        private var idx = 0
        override suspend fun getChanges(baseUrl: String, bearerToken: String, since: Long): PageResult {
            sinceCalls += since
            return pages.getOrElse(idx++) { pages.last() }
        }
        override suspend fun getChoresLegacy(baseUrl: String, bearerToken: String): PageResult {
            legacyCalls++
            return legacy ?: error("no legacy result configured")
        }
    }

    private fun chore(id: Int, name: String = "c$id") = JSONObject().put("id", id).put("name", name)
    private fun ok(chores: List<JSONObject>, deleted: Set<Int>, cursor: Long, more: Boolean) =
        PageResult.Ok(chores, deleted, cursor, more)

    private lateinit var store: SyncStateStore
    private lateinit var config: GetServerConfigUseCase

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("donetick_sync_state", Context.MODE_PRIVATE).edit().clear().commit()
        store = SyncStateStore(ctx)
        config = mockk()
        coEvery { config.getCurrentConfig() } returns ServerConfig("http://s", isConfigured = true)
    }

    private fun coordinator(api: SyncApi) = ChoreSyncCoordinator(api, store, config)

    @Test
    fun `pages through hasMore, merging chores and advancing cursor`() = runTest {
        val api = FakeSyncApi(
            listOf(
                ok(listOf(chore(1), chore(2)), emptySet(), cursor = 229, more = true),
                ok(emptyList(), emptySet(), cursor = 429, more = true),
                ok(listOf(chore(3)), setOf(2), cursor = 445, more = false),
            ),
        )
        val result = coordinator(api).sync("tok")

        assertEquals(listOf(0L, 229L, 429L), api.sinceCalls)
        result as ChoreSyncCoordinator.Outcome.Success
        assertTrue(result.changed)
        assertEquals(setOf(1, 3), result.chores.map { it.getInt("id") }.toSet())
        assertEquals(445L, store.cursor)
    }

    @Test
    fun `empty delta is a Success with changed=false and an advanced cursor`() = runTest {
        store.resetIfServerChanged("http://s")
        store.applyDelta(listOf(chore(1)), emptySet(), 100L)
        val api = FakeSyncApi(listOf(ok(emptyList(), emptySet(), cursor = 120, more = false)))

        val result = coordinator(api).sync("tok")

        result as ChoreSyncCoordinator.Outcome.Success
        assertEquals(false, result.changed)
        assertEquals(120L, store.cursor)
        assertEquals(listOf(1), result.chores.map { it.getInt("id") })
    }

    @Test
    fun `Unauthorized short-circuits without mutating the store`() = runTest {
        store.resetIfServerChanged("http://s")
        store.applyDelta(listOf(chore(1)), emptySet(), 100L)
        val api = FakeSyncApi(listOf(PageResult.Unauthorized))

        assertTrue(coordinator(api).sync("tok") is ChoreSyncCoordinator.Outcome.Unauthorized)
        assertEquals(100L, store.cursor)
        assertEquals(listOf(1), store.snapshotChores().map { it.getInt("id") })
    }

    @Test
    fun `transport error short-circuits without mutating the store`() = runTest {
        store.resetIfServerChanged("http://s")
        store.applyDelta(listOf(chore(1)), emptySet(), 100L)
        val api = FakeSyncApi(listOf(PageResult.TransportError(java.io.IOException("x"))))

        val r = coordinator(api).sync("tok")
        assertTrue(r is ChoreSyncCoordinator.Outcome.Failed)
        assertEquals(100L, store.cursor)
    }

    @Test
    fun `first-page NotSupported falls back to legacy chores endpoint`() = runTest {
        val api = FakeSyncApi(
            pages = listOf(PageResult.NotSupported),
            legacy = ok(listOf(chore(1), chore(2), chore(3)), emptySet(), cursor = 0, more = false),
        )
        val result = coordinator(api).sync("tok")

        assertEquals(1, api.legacyCalls)
        result as ChoreSyncCoordinator.Outcome.Success
        assertTrue(result.changed)
        assertEquals(3, result.chores.size)
    }

    @Test
    fun `MAX_PAGES cap stops the loop and persists progress`() = runTest {
        val api = FakeSyncApi(listOf(ok(listOf(chore(1)), emptySet(), cursor = 5, more = true)))
        val result = coordinator(api).sync("tok")

        assertEquals(100, api.sinceCalls.size)
        assertTrue(result is ChoreSyncCoordinator.Outcome.Success)
        assertEquals(5L, store.cursor)
    }

    @Test
    fun `NoServer when not configured`() = runTest {
        coEvery { config.getCurrentConfig() } returns ServerConfig("", isConfigured = false)
        val api = FakeSyncApi(listOf(ok(emptyList(), emptySet(), 0, false)))

        assertTrue(coordinator(api).sync("tok") is ChoreSyncCoordinator.Outcome.NoServer)
        assertTrue(api.sinceCalls.isEmpty())
    }

    @Test
    fun `switching server URL resets the cursor before syncing`() = runTest {
        store.resetIfServerChanged("http://old")
        store.applyDelta(listOf(chore(1)), emptySet(), 900L)

        // config now points at http://s -> coordinator should reset then sync from 0
        val api = FakeSyncApi(listOf(ok(listOf(chore(2)), emptySet(), cursor = 3, more = false)))
        val result = coordinator(api).sync("tok")

        assertEquals(listOf(0L), api.sinceCalls)
        result as ChoreSyncCoordinator.Outcome.Success
        assertEquals(listOf(2), result.chores.map { it.getInt("id") })
        assertNull(result.chores.firstOrNull { it.getInt("id") == 1 })
    }
}
