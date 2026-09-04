package org.chaosorderx.donetick.ui.webview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.chaosorderx.donetick.data.mapper.ChoreJsonMapper
import org.chaosorderx.donetick.ui.theme.DoneTickTheme
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
                    put("status", chore.status)
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
        val choresList = ChoreJsonMapper.fromEnvelope(choresData)

        setContent {
            DoneTickTheme {
                ChoresListScreen(
                    choresList = choresList,
                    onBackClick = { finish() }
                )
            }
        }
    }

}
