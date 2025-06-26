package com.donetick.app.ui.webview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.donetick.app.ui.theme.DoneTickTheme
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dedicated activity for displaying the list of chores with notifications.
 *
 * This activity provides a standalone view for chores management and handles:
 * - Receiving chores data from WebViewActivity via Intent extras
 * - Displaying chores with notification status and scheduling information
 * - Standard back navigation to parent WebViewActivity
 *
 * Architecture: Replaces the previous HorizontalPager approach with a separate
 * activity for cleaner separation of concerns and better user experience.
 */
class ChoresListActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_CHORES_DATA = "extra_chores_data"

        /**
         * Creates an intent to launch ChoresListActivity with chores data
         */
        fun createIntent(context: Context, choresData: String): Intent {
            return Intent(context, ChoresListActivity::class.java).apply {
                putExtra(EXTRA_CHORES_DATA, choresData)
            }
        }

        /**
         * Creates an intent to launch ChoresListActivity with chores list
         */
        fun createIntent(context: Context, choresList: List<ChoreItem>): Intent {
            // Convert chores list back to JSON format for consistency
            val jsonArray = JSONArray()
            choresList.forEach { chore ->
                val choreJson = JSONObject().apply {
                    put("id", chore.id)
                    put("name", chore.name)
                    put("assignedTo", chore.assignedTo)
                    put("nextDueDate", chore.nextDueDate)
                    put("isCompleted", chore.isCompleted)
                    put("frequencyType", chore.frequencyType)
                    put("frequency", chore.frequency)
                    put("description", chore.description)
                    put("notification", chore.notification)
                    put("isActive", chore.isActive)
                    put("priority", chore.priority)
                    chore.notificationMetadata?.let { metadata ->
                        put("notificationMetadata", JSONObject().apply {
                            put("dueDate", metadata.dueDate)
                        })
                    }
                }
                jsonArray.put(choreJson)
            }
            
            val choresData = JSONObject().apply {
                put("res", jsonArray)
            }.toString()
            
            return createIntent(context, choresData)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get chores data from intent
        val choresData = intent.getStringExtra(EXTRA_CHORES_DATA) ?: ""
        val choresList = parseChoresJson(choresData)

        setContent {
            DoneTickTheme {
                ChoresListScreen(
                    choresList = choresList,
                    onBackClick = { finish() }
                )
            }
        }
    }

    /**
     * Parses the JSON chores data into ChoreItem objects
     * This is a simplified version of the parsing logic from WebViewViewModel
     */
    private fun parseChoresJson(jsonData: String): List<ChoreItem> {
        return try {
            if (jsonData.isEmpty()) return emptyList()
            
            val jsonObject = JSONObject(jsonData)
            val resArray = jsonObject.optJSONArray("res") ?: JSONArray(jsonData)
            val choresList = mutableListOf<ChoreItem>()

            for (i in 0 until resArray.length()) {
                val choreJson = resArray.getJSONObject(i)
                
                val notificationMetadata = choreJson.optJSONObject("notificationMetadata")?.let { metadata ->
                    NotificationMetadata(
                        dueDate = metadata.optBoolean("dueDate", false)
                    )
                }

                val chore = ChoreItem(
                    id = choreJson.getInt("id"),
                    name = choreJson.getString("name"),
                    assignedTo = if (choreJson.has("assignedTo") && !choreJson.isNull("assignedTo")) {
                        choreJson.getInt("assignedTo")
                    } else null,
                    nextDueDate = choreJson.optString("nextDueDate").takeIf { it.isNotEmpty() },
                    isCompleted = choreJson.optBoolean("isCompleted", false),
                    frequencyType = choreJson.optString("frequencyType").takeIf { it.isNotEmpty() },
                    frequency = choreJson.optInt("frequency", 1),
                    description = choreJson.optString("description").takeIf { it.isNotEmpty() },
                    notification = choreJson.optBoolean("notification", false),
                    notificationMetadata = notificationMetadata,
                    isActive = choreJson.optBoolean("isActive", true),
                    priority = choreJson.optInt("priority", 0)
                )
                choresList.add(chore)
            }

            choresList
        } catch (e: Exception) {
            android.util.Log.e("ChoresListActivity", "Error parsing chores JSON", e)
            emptyList()
        }
    }
}
