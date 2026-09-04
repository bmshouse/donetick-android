package org.chaosorderx.donetick.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.chaosorderx.donetick.data.remote.PageResult
import org.chaosorderx.donetick.data.remote.SyncApi
import org.chaosorderx.donetick.domain.usecase.GetServerConfigUseCase
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives one foreground sync: pages `/sync/changes` from the persisted cursor, merges the
 * delta into [SyncStateStore], and returns the full merged chore snapshot for the caller to
 * hand to notification scheduling. Falls back to the legacy `/chores/` endpoint on servers
 * that predate the sync engine.
 */
@Singleton
class ChoreSyncCoordinator @Inject constructor(
    private val api: SyncApi,
    private val store: SyncStateStore,
    private val getServerConfig: GetServerConfigUseCase,
) {
    sealed interface Outcome {
        /** [chores] is the full merged snapshot; [changed] is true if anything moved this run. */
        data class Success(val chores: List<JSONObject>, val changed: Boolean) : Outcome
        data object Unauthorized : Outcome
        data object NoServer : Outcome
        data class Failed(val cause: Throwable?) : Outcome
    }

    suspend fun sync(bearerToken: String): Outcome = withContext(Dispatchers.IO) {
        val config = getServerConfig.getCurrentConfig()
        if (!config.isConfigured || config.url.isEmpty()) return@withContext Outcome.NoServer
        val base = config.getNormalizedUrl()
        store.resetIfServerChanged(base)

        val upserts = LinkedHashMap<Int, JSONObject>()
        val deletes = HashSet<Int>()
        var cursor = store.cursor
        var pages = 0
        var changed = false

        while (true) {
            when (val page = api.getChanges(base, bearerToken, cursor)) {
                is PageResult.Ok -> {
                    page.chores.forEach { c -> upserts[c.optInt("id")] = c }
                    deletes += page.deletedChoreIds
                    changed = changed ||
                        page.chores.isNotEmpty() ||
                        page.deletedChoreIds.isNotEmpty()
                    cursor = page.cursor
                    pages++
                    if (!page.hasMore || pages >= MAX_PAGES) break
                }
                PageResult.NotSupported -> {
                    // First page 404/501 => old server, use the legacy endpoint.
                    if (pages == 0) return@withContext legacyFallback(base, bearerToken)
                    break // mid-stream is unexpected; persist what we have
                }
                PageResult.Unauthorized -> return@withContext Outcome.Unauthorized
                is PageResult.TransportError -> return@withContext Outcome.Failed(page.cause)
                is PageResult.BadResponse -> return@withContext Outcome.Failed(null)
            }
        }

        store.applyDelta(upserts.values.toList(), deletes, cursor)
        Outcome.Success(store.snapshotChores(), changed)
    }

    private suspend fun legacyFallback(base: String, bearerToken: String): Outcome =
        when (val page = api.getChoresLegacy(base, bearerToken)) {
            is PageResult.Ok -> {
                store.replaceAll(page.chores)
                Outcome.Success(store.snapshotChores(), changed = true)
            }
            PageResult.Unauthorized -> Outcome.Unauthorized
            is PageResult.TransportError -> Outcome.Failed(page.cause)
            else -> Outcome.Failed(null)
        }

    private companion object {
        /** 100 pages * 200 rows = 20k sync versions per foreground cycle. */
        const val MAX_PAGES = 100
    }
}
