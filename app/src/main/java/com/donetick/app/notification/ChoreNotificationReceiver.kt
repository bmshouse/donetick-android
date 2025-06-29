package com.donetick.app.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.Html
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.donetick.app.R
import com.donetick.app.ui.MainActivity

/**
 * BroadcastReceiver that handles scheduled chore notifications
 */
class ChoreNotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ChoreNotificationReceiver"
        private const val CHANNEL_ID = "chore_notifications"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received notification broadcast")
        
        val choreId = intent.getIntExtra("chore_id", -1)
        val choreName = intent.getStringExtra("chore_name") ?: "Unknown Chore"
        val choreDescription = intent.getStringExtra("chore_description") ?: ""
        
        if (choreId == -1) {
            Log.e(TAG, "Invalid chore ID received")
            return
        }
        
        showNotification(context, choreId, choreName, choreDescription)
    }
    
    private fun showNotification(context: Context, choreId: Int, choreName: String, choreDescription: String) {
        // Check if a notification for this chore already exists
        if (isNotificationAlreadyActive(context, choreId)) {
            Log.d(TAG, "Notification for chore '$choreName' (ID: $choreId) already exists, skipping")
            return
        }

        // Create intent to open the app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chore_id", choreId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            choreId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Convert HTML description to plain text for notification
        val notificationText = htmlToNotificationText(choreDescription).takeIf { it.isNotEmpty() }
            ?: "This chore is now due"

        // Build the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentTitle("Chore Due: $choreName")
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        // Show the notification
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(choreId, notification)
            Log.d(TAG, "Showed notification for chore: $choreName")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException showing notification", e)
        }
    }

    /**
     * Checks if a notification with the given ID is already active
     * @param context The context to use for accessing the notification manager
     * @param notificationId The notification ID to check
     * @return true if a notification with this ID is already active, false otherwise
     */
    private fun isNotificationAlreadyActive(context: Context, notificationId: Int): Boolean {
        return try {
            // Use getActiveNotifications() for API 23+
            val notificationManager = NotificationManagerCompat.from(context)
            val activeNotifications = notificationManager.activeNotifications
            val isActive = activeNotifications.any { it.id == notificationId }
            Log.d(TAG, "Checking active notifications: found ${activeNotifications.size} active, notification ID $notificationId is ${if (isActive) "active" else "not active"}")
            isActive
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active notifications", e)
            // If we can't check, assume it's not active to avoid blocking notifications
            false
        }
    }

    /**
     * Converts HTML-formatted text to plain text suitable for notifications
     * Handles common HTML tags like <p>, <li>, <ul>, <ol>, <br>, etc.
     */
    private fun htmlToNotificationText(htmlText: String?): String {
        if (htmlText.isNullOrEmpty()) return ""

        try {
            // Use Html.fromHtml to parse HTML and convert to plain text
            val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(htmlText, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(htmlText)
            }

            // Convert to string and clean up extra whitespace
            val plainText = spanned.toString()
                .replace(Regex("\\s+"), " ") // Replace multiple whitespace with single space
                .trim()

            // Limit length for notification display (Android notifications have character limits)
            return if (plainText.length > 200) {
                plainText.take(197) + "..."
            } else {
                plainText
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting HTML to plain text: $htmlText", e)
            // Fallback: return original text with basic HTML tag removal
            return htmlText.replace(Regex("<[^>]*>"), "").trim()
        }
    }
}
