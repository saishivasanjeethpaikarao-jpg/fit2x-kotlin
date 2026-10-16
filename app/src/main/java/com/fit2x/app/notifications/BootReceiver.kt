package com.fit2x.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Exact alarms do not survive a reboot — re-register the whole reminder
 * schedule when the device finishes booting. Also covers the WorkManager
 * fallback path.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val job = SupervisorJob()
        CoroutineScope(job + Dispatchers.IO).launch {
            try {
                NotificationHelper.scheduleAll(context.applicationContext)
            } catch (_: Exception) {
                // Best-effort: a missed re-schedule here is retried on next app launch.
            } finally {
                pending.finish()
                job.cancel()
            }
        }
    }
}
