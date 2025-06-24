package com.donetick.app.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        
        // Build the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentTitle("Chore Due: $choreName")
            .setContentText(choreDescription.takeIf { it.isNotEmpty() } ?: "This chore is now due")
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
}
