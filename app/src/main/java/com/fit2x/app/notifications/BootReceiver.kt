package com.fit2x.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * WorkManager's periodic schedule does not survive a reboot on its own —
 * re-schedule all reminders when the device finishes booting.
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        scope.launch {
            try {
                NotificationHelper.scheduleAll(context.applicationContext)
            } catch (_: Exception) {
                // Best-effort: a missed re-schedule here is retried on next app launch.
            } finally {
                pending.finish()
            }
        }
    }
}
