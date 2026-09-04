package org.chaosorderx.donetick.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Outcome of fetching a single sync page (or the legacy full-list fallback).
 */
sealed interface PageResult {
    data class Ok(
        val chores: List<JSONObject>,
        val deletedChoreIds: Set<Int>,
        val cursor: Long,
        val hasMore: Boolean,
    ) : PageResult

    /** 401 / 403 — the JWT borrowed from the WebView is missing or expired. */
    data object Unauthorized : PageResult

    /** 404 / 501 — server predates the sync engine; caller falls back to the legacy endpoint. */
    data object NotSupported : PageResult

    data class TransportError(val cause: Throwable) : PageResult
    data class BadResponse(val code: Int, val snippet: String) : PageResult
}

interface SyncApi {
    /** `GET {baseUrl}/api/v1/sync/changes?since={since}` */
    suspend fun getChanges(baseUrl: String, bearerToken: String, since: Long): PageResult

    /** `GET {baseUrl}/api/v1/chores/` — legacy fallback; returns everything as one page. */
    suspend fun getChoresLegacy(baseUrl: String, bearerToken: String): PageResult
}

class HttpSyncApi(private val http: HttpClient) : SyncApi {

    override suspend fun getChanges(baseUrl: String, bearerToken: String, since: Long): PageResult =
        request("$baseUrl/api/v1/sync/changes?since=$since", bearerToken, ::parseChangesPage)

    override suspend fun getChoresLegacy(baseUrl: String, bearerToken: String): PageResult =
        request("$baseUrl/api/v1/chores/", bearerToken, ::parseLegacyPage)

    private suspend fun request(
        url: String,
        bearerToken: String,
        parse: (String) -> PageResult,
    ): PageResult = withContext(Dispatchers.IO) {
        val resp = try {
            http.get(url, bearerToken)
        } catch (e: Exception) {
            return@withContext PageResult.TransportError(e)
        }
        when {
            resp.code == 401 || resp.code == 403 -> PageResult.Unauthorized
            resp.code == 404 || resp.code == 501 -> PageResult.NotSupported
            resp.code !in 200..299 -> PageResult.BadResponse(resp.code, resp.body.take(200))
            else -> try {
                parse(resp.body)
            } catch (e: JSONException) {
                PageResult.BadResponse(resp.code, resp.body.take(200))
            }
        }
    }

    private fun parseChangesPage(body: String): PageResult {
        val root = JSONObject(body)
        val choresArr = root.optJSONObject("changes")?.optJSONArray("chores")
        val chores = buildList {
            if (choresArr != null) {
                for (i in 0 until choresArr.length()) choresArr.optJSONObject(i)?.let(::add)
            }
        }
        val delArr = root.optJSONObject("deletions")?.optJSONArray("chores")
        val deleted = buildSet {
            if (delArr != null) for (i in 0 until delArr.length()) add(delArr.getInt(i))
        }
        return PageResult.Ok(
            chores = chores,
            deletedChoreIds = deleted,
            cursor = root.optLong("cursor", 0L),
            hasMore = root.optBoolean("hasMore", false),
        )
    }

    private fun parseLegacyPage(body: String): PageResult {
        val arr = JSONObject(body).optJSONArray("res")
            ?: return PageResult.BadResponse(200, body.take(200))
        val chores = buildList {
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::add)
        }
        return PageResult.Ok(chores, emptySet(), cursor = 0L, hasMore = false)
    }
}
