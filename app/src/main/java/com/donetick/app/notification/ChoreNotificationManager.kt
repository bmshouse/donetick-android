package com.donetick.app.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.Html
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.donetick.app.R
import com.donetick.app.ui.MainActivity
import com.donetick.app.ui.webview.ChoreItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages chore notifications including scheduling, updating, and canceling
 */
@Singleton
class ChoreNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ChoreNotificationManager"
        private const val CHANNEL_ID = "chore_notifications"
        private const val CHANNEL_NAME = "Chore Reminders"
        private const val CHANNEL_DESCRIPTION = "Notifications for upcoming chores"
        private const val NOTIFICATION_REQUEST_CODE_BASE = 10000
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Keep track of scheduled notification IDs to properly cancel them
    private val scheduledNotificationIds = mutableSetOf<Int>()

    init {
        createNotificationChannel()
    }

    /**
     * Creates the notification channel for chore notifications
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules notifications for all chores that have notification enabled
     */
    fun scheduleChoreNotifications(chores: List<ChoreItem>) {
        Log.d(TAG, "Scheduling notifications for ${chores.size} chores")

        // Cancel ALL existing chore notifications first (not just current chores)
        cancelAllChoreNotifications()

        // Schedule new notifications for chores with notification enabled
        val notificationChores = chores.filter { it.notification && it.isActive && it.nextDueDate != null }
        Log.d(TAG, "Found ${notificationChores.size} chores with notifications enabled")

        notificationChores.forEach { chore ->
            Log.d(TAG, "Processing chore: ${chore.name} (ID: ${chore.id})")
            scheduleChoreNotification(chore)
        }

        Log.d(TAG, "Completed scheduling notifications")
    }

    /**
     * Schedules a notification for a specific chore
     */
    private fun scheduleChoreNotification(chore: ChoreItem) {
        try {
            val dueDate = parseIsoDate(chore.nextDueDate ?: return)
            val notificationTime = calculateNotificationTime(dueDate)

            Log.d(TAG, "Scheduling notification for chore '${chore.name}' (ID: ${chore.id}) due at ${Date(dueDate)}")

            if (notificationTime <= System.currentTimeMillis()) {
                Log.d(TAG, "Chore ${chore.name} is already due or past due, showing immediate notification")
                showImmediateNotification(chore)
                // Track this notification ID even for immediate notifications
                scheduledNotificationIds.add(chore.id)
                return
            }

            val intent = Intent(context, ChoreNotificationReceiver::class.java).apply {
                putExtra("chore_id", chore.id)
                putExtra("chore_name", chore.name)
                putExtra("chore_description", chore.description ?: "")
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_REQUEST_CODE_BASE + chore.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule the alarm
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                notificationTime,
                pendingIntent
            )

            // Track this notification ID
            scheduledNotificationIds.add(chore.id)

            Log.d(TAG, "Scheduled future notification for chore '${chore.name}' at ${Date(notificationTime)}")

        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling notification for chore ${chore.name}", e)
        }
    }

    /**
     * Shows an immediate notification for overdue chores
     */
    private fun showImmediateNotification(chore: ChoreItem) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission, cannot show notification")
            return
        }

        // Check if a notification for this chore already exists
        if (isNotificationAlreadyActive(chore.id)) {
            Log.d(TAG, "Notification for chore '${chore.name}' (ID: ${chore.id}) already exists, skipping")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            chore.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Convert HTML description to plain text for notification
        val notificationText = htmlToNotificationText(chore.description).takeIf { it.isNotEmpty() }
            ?: "This chore is now due"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentTitle("Chore Due: ${chore.name}")
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(chore.id, notification)
            Log.d(TAG, "Showed immediate notification for chore '${chore.name}'")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException showing notification", e)
        }
    }

    /**
     * Checks if a notification with the given ID is already active
     * @param notificationId The notification ID to check
     * @return true if a notification with this ID is already active, false otherwise
     */
    private fun isNotificationAlreadyActive(notificationId: Int): Boolean {
        return try {
            // Use getActiveNotifications() for API 23+
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
     * Cancels all scheduled notifications (for all previously tracked chores)
     */
    private fun cancelAllChoreNotifications() {
        Log.d(TAG, "Cancelling all ${scheduledNotificationIds.size} scheduled notifications")

        // Create a copy to avoid ConcurrentModificationException
        val idsToCancel = scheduledNotificationIds.toSet()
        idsToCancel.forEach { choreId ->
            cancelSingleChoreNotification(choreId)
        }
        scheduledNotificationIds.clear()
    }

    /**
     * Cancels all scheduled notifications for the given chores (legacy method)
     */
    private fun cancelAllChoreNotifications(chores: List<ChoreItem>) {
        chores.forEach { chore ->
            cancelChoreNotification(chore.id)
        }
    }

    /**
     * Cancels a specific chore notification without modifying the tracked set (internal use)
     */
    private fun cancelSingleChoreNotification(choreId: Int) {
        val intent = Intent(context, ChoreNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_REQUEST_CODE_BASE + choreId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }

        // Also cancel any currently showing notification
        notificationManager.cancel(choreId)

        Log.d(TAG, "Cancelled notification for chore ID: $choreId")
    }

    /**
     * Cancels a specific chore notification (public method for external use)
     */
    fun cancelChoreNotification(choreId: Int) {
        Log.d(TAG, "cancelChoreNotification called for chore ID: $choreId")
        Log.d(TAG, "Current tracked notifications: $scheduledNotificationIds")

        cancelSingleChoreNotification(choreId)

        // Remove from tracked notifications
        val wasRemoved = scheduledNotificationIds.remove(choreId)
        Log.d(TAG, "Removed chore $choreId from tracked notifications: $wasRemoved")
        Log.d(TAG, "Remaining tracked notifications: $scheduledNotificationIds")
    }

    /**
     * Calculates when to show the notification based on the due date
     */
    private fun calculateNotificationTime(dueDate: Long): Long {
        // For now, show notification at the due time
        // You can customize this logic based on chore.notificationMetadata
        return dueDate
    }

    /**
     * Parses ISO date string to timestamp
     */
    private fun parseIsoDate(dateString: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date: $dateString", e)
            0L
        }
    }

    /**
     * Gets the count of currently scheduled notifications (for debugging)
     */
    fun getScheduledNotificationCount(): Int {
        return scheduledNotificationIds.size
    }

    /**
     * Gets the list of scheduled notification IDs (for debugging)
     */
    fun getScheduledNotificationIds(): Set<Int> {
        return scheduledNotificationIds.toSet()
    }

    /**
     * Checks if the app has notification permission
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
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
