package org.chaosorderx.donetick.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.chaosorderx.donetick.R
import org.chaosorderx.donetick.ui.MainActivity
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Warns the user [WARN_WINDOW_MS] before the Donetick session token expires, so they open the
 * app and the token gets refreshed. Each refresh reschedules the warning for the new expiry.
 */
@Singleton
class SessionExpiryNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SessionExpiryNotifier"
        const val CHANNEL_ID = "session_notifications"
        private const val CHANNEL_NAME = "Session Expiry"
        private const val CHANNEL_DESCRIPTION = "Reminders to reopen the app before your login expires"
        const val NOTIFICATION_ID = 9001
        private const val REQUEST_CODE = 9001
        const val EXTRA_EXPIRY_MS = "expiry_ms"
        const val EXTRA_CAN_REFRESH = "can_refresh"

        val WARN_WINDOW_MS: Long = TimeUnit.DAYS.toMillis(5)

        /** When the warning should fire for a token expiring at [expiryMs]. */
        fun warnAt(expiryMs: Long): Long = expiryMs - WARN_WINDOW_MS

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = CHANNEL_DESCRIPTION }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        fun showWarning(context: Context, expiryMs: Long, canRefresh: Boolean) {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
            if (!hasPermission) {
                Log.w(TAG, "No notification permission, cannot show session warning")
                return
            }
            ensureChannel(context)

            val openApp = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val expiresOn = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiryMs))
            val action = if (canRefresh) "Open the app" else "Log in again"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Donetick login expiring soon")
                .setContentText("$action before $expiresOn to keep chore notifications working")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException showing session warning", e)
            }
        }
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        ensureChannel(context)
    }

    /**
     * Arms the warning for a token expiring at [expiryMs], replacing any earlier one. If the
     * warning time has already passed, the warning is shown immediately. [canRefresh] picks the
     * wording: opening the app only helps when the server can refresh the token.
     */
    fun schedule(expiryMs: Long, canRefresh: Boolean) {
        cancelAlarm()

        val fireAt = warnAt(expiryMs)
        if (fireAt <= System.currentTimeMillis()) {
            Log.d(TAG, "Session within warning window, notifying now (expires ${Date(expiryMs)})")
            showWarning(context, expiryMs, canRefresh)
            return
        }

        // A fresh token supersedes any warning still on screen.
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        // Inexact is fine for a days-ahead reminder and needs no exact-alarm permission.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, alarmIntent(expiryMs, canRefresh, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        Log.d(TAG, "Session warning scheduled for ${Date(fireAt)} (token expires ${Date(expiryMs)})")
    }

    private fun cancelAlarm() {
        alarmIntent(0L, false, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun alarmIntent(expiryMs: Long, canRefresh: Boolean, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SessionExpiryReceiver::class.java)
            .putExtra(EXTRA_EXPIRY_MS, expiryMs)
            .putExtra(EXTRA_CAN_REFRESH, canRefresh),
        flag or PendingIntent.FLAG_IMMUTABLE
    )
}
