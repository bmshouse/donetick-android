package org.chaosorderx.donetick.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Fires the "session expiring" warning when its alarm goes off. */
class SessionExpiryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val expiryMs = intent.getLongExtra(SessionExpiryNotifier.EXTRA_EXPIRY_MS, 0L)
        if (expiryMs == 0L) {
            Log.e("SessionExpiryReceiver", "Missing expiry on session warning broadcast")
            return
        }
        SessionExpiryNotifier.showWarning(
            context,
            expiryMs,
            intent.getBooleanExtra(SessionExpiryNotifier.EXTRA_CAN_REFRESH, true)
        )
    }
}
