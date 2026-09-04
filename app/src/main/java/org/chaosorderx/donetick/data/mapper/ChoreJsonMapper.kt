package org.chaosorderx.donetick.data.mapper

import org.chaosorderx.donetick.ui.webview.ChoreItem
import org.chaosorderx.donetick.ui.webview.NotificationMetadata
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Single source of truth for turning Donetick chore JSON into [ChoreItem].
 *
 * The sync API's chore objects (`changes.chores[]`) and the legacy `GET /api/v1/chores/`
 * envelope (`{"res":[...]}`) use the same chore model, and [ChoresListActivity]'s intent
 * round-trip re-serialises [ChoreItem] into the same shape. This replaces the three
 * near-duplicate parsers that previously drifted apart.
 */
object ChoreJsonMapper {

    fun fromJson(o: JSONObject): ChoreItem {
        val notificationMetadata = o.optJSONObject("notificationMetadata")?.let { metadata ->
            NotificationMetadata(dueDate = metadata.optBoolean("dueDate", false))
        }
        return ChoreItem(
            id = o.optInt("id", 0),
            name = o.optString("name", ""),
            assignedTo = o.intOrNull("assignedTo")?.takeIf { it != 0 },
            nextDueDate = o.stringOrNull("nextDueDate"),
            // Honoured only when explicitly present (the intent round-trip carries it);
            // server payloads never do. Never derived from `status`.
            isCompleted = o.optBoolean("isCompleted", false),
            frequencyType = o.stringOrNull("frequencyType"),
            frequency = o.optInt("frequency", 1),
            description = o.stringOrNull("description"),
            notification = o.optBoolean("notification", false),
            notificationMetadata = notificationMetadata,
            isActive = o.optBoolean("isActive", true),
            priority = o.optInt("priority", 0),
            status = o.optInt("status", 0),
        )
    }

    fun fromJsonList(objects: List<JSONObject>): List<ChoreItem> = objects.map(::fromJson)

    /**
     * Parses a `{"res":[...]}` object, a bare `[...]` array, or an empty string.
     * Returns an empty list on malformed input rather than throwing.
     */
    fun fromEnvelope(json: String): List<ChoreItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = runCatching { JSONObject(json).optJSONArray("res") }.getOrNull()
                ?: JSONArray(json)
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::fromJson) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)
}
