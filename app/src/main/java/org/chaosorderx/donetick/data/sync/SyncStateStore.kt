package org.chaosorderx.donetick.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the native sync cursor and the merged chore snapshot.
 *
 * Unencrypted — this is chore data, not credentials (the borrowed JWT is never stored). Kept
 * in its own SharedPreferences file, separate from `donetick_secure_prefs`. Persisting rather
 * than holding in memory means an Activity/process restart resumes from the last cursor
 * instead of re-pulling a full `since=0` snapshot, and a one-chore delta never drops the rest
 * of the snapshot.
 */
@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("donetick_sync_state", Context.MODE_PRIVATE)

    val cursor: Long get() = prefs.getLong(KEY_CURSOR, 0L)

    /** Reset the store if it holds data for a different server. Call before each sync. */
    fun resetIfServerChanged(serverBase: String) {
        if (prefs.getString(KEY_SERVER, null) != serverBase) {
            clearInternal(serverBase)
        }
    }

    /** The merged chore objects. Empty if nothing has synced yet or the snapshot was corrupt. */
    fun snapshotChores(): List<JSONObject> {
        val map = readMap() ?: return emptyList()
        return map.keys().asSequence().mapNotNull { map.optJSONObject(it) }.toList()
    }

    /**
     * Apply a delta: upsert [upserts] by `id`, drop [deletedIds], store [newCursor].
     * One atomic write. No-ops (leaving a full resync for next cycle) if the snapshot was corrupt.
     */
    fun applyDelta(upserts: List<JSONObject>, deletedIds: Set<Int>, newCursor: Long) {
        val map = readMap() ?: return
        upserts.forEach { chore ->
            val id = chore.optInt("id", 0)
            if (id != 0) map.put(id.toString(), chore)
        }
        deletedIds.forEach { map.remove(it.toString()) }
        prefs.edit {
            putString(KEY_CHORES, map.toString())
            putLong(KEY_CURSOR, newCursor)
        }
    }

    /** Replace the whole snapshot (legacy `/chores/` fallback). Cursor left untouched. */
    fun replaceAll(chores: List<JSONObject>) {
        val map = JSONObject()
        chores.forEach { chore ->
            val id = chore.optInt("id", 0)
            if (id != 0) map.put(id.toString(), chore)
        }
        prefs.edit { putString(KEY_CHORES, map.toString()) }
    }

    fun clear() = clearInternal(null)

    private fun clearInternal(serverBase: String?) {
        prefs.edit {
            clear()
            if (serverBase != null) putString(KEY_SERVER, serverBase)
        }
    }

    /**
     * @return the stored chore map (empty [JSONObject] when nothing is stored), or `null` when
     * the stored value was unparseable — in which case it is purged along with the cursor so the
     * next sync starts from `since=0`.
     */
    private fun readMap(): JSONObject? {
        val raw = prefs.getString(KEY_CHORES, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (e: JSONException) {
            Log.w(TAG, "Corrupt chore snapshot; purging for a full resync", e)
            prefs.edit {
                remove(KEY_CHORES)
                remove(KEY_CURSOR)
            }
            null
        }
    }

    private companion object {
        const val TAG = "SyncStateStore"
        const val KEY_CURSOR = "cursor"
        const val KEY_SERVER = "server_base"
        const val KEY_CHORES = "chores_by_id"
    }
}
