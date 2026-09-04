package org.chaosorderx.donetick.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncStateStoreTest {

    private lateinit var context: Context
    private lateinit var store: SyncStateStore

    private fun chore(id: Int, name: String = "c$id") =
        JSONObject().put("id", id).put("name", name)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("donetick_sync_state", Context.MODE_PRIVATE).edit().clear().commit()
        store = SyncStateStore(context)
    }

    @Test
    fun `initial state is empty with cursor zero`() {
        assertEquals(0L, store.cursor)
        assertTrue(store.snapshotChores().isEmpty())
    }

    @Test
    fun `applyDelta upserts chores and stores cursor`() {
        store.applyDelta(listOf(chore(1), chore(2)), emptySet(), 229L)
        assertEquals(229L, store.cursor)
        assertEquals(setOf(1, 2), store.snapshotChores().map { it.getInt("id") }.toSet())
    }

    @Test
    fun `applyDelta replaces an existing chore and removes deleted ones`() {
        store.applyDelta(listOf(chore(1, "old"), chore(2)), emptySet(), 10L)
        store.applyDelta(listOf(chore(1, "new")), setOf(2), 20L)

        val snap = store.snapshotChores().associateBy { it.getInt("id") }
        assertEquals(setOf(1), snap.keys)
        assertEquals("new", snap.getValue(1).getString("name"))
        assertEquals(20L, store.cursor)
    }

    @Test
    fun `state survives a new store instance (process restart)`() {
        store.applyDelta(listOf(chore(1), chore(2), chore(3)), emptySet(), 500L)

        val reopened = SyncStateStore(context)
        assertEquals(500L, reopened.cursor)
        assertEquals(3, reopened.snapshotChores().size)
    }

    @Test
    fun `replaceAll swaps the snapshot without touching the cursor`() {
        store.applyDelta(listOf(chore(1), chore(2)), emptySet(), 42L)
        store.replaceAll(listOf(chore(9), chore(8), chore(7)))

        assertEquals(42L, store.cursor)
        assertEquals(setOf(7, 8, 9), store.snapshotChores().map { it.getInt("id") }.toSet())
    }

    @Test
    fun `resetIfServerChanged clears on a new server but not on the same one`() {
        store.resetIfServerChanged("http://a")
        store.applyDelta(listOf(chore(1)), emptySet(), 100L)

        store.resetIfServerChanged("http://a")
        assertEquals(100L, store.cursor)

        store.resetIfServerChanged("http://b")
        assertEquals(0L, store.cursor)
        assertTrue(store.snapshotChores().isEmpty())
    }

    @Test
    fun `clear resets everything`() {
        store.applyDelta(listOf(chore(1)), emptySet(), 100L)
        store.clear()
        assertEquals(0L, store.cursor)
        assertTrue(store.snapshotChores().isEmpty())
    }

    @Test
    fun `corrupt snapshot is purged and forces a full resync`() {
        store.applyDelta(listOf(chore(1)), emptySet(), 100L)
        context.getSharedPreferences("donetick_sync_state", Context.MODE_PRIVATE)
            .edit().putString("chores_by_id", "{not valid json").commit()

        assertTrue(store.snapshotChores().isEmpty())
        assertEquals(0L, store.cursor)
    }
}
