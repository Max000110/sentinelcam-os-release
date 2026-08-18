package com.sentinelcam.node.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

object WatchdogManager {
    private const val TAG = "SentinelCam.Watchdog"
    private const val WATCHDOG_INTERVAL_MS = 30_000L // Check every 30 seconds

    fun scheduleWatchdog(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BootReceiver::class.java).apply {
            action = Intent.ACTION_MY_PACKAGE_REPLACED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS,
            WATCHDOG_INTERVAL_MS,
            pendingIntent
        )
        Log.i(TAG, "Watchdog ping scheduled every ${WATCHDOG_INTERVAL_MS / 1000}s")
    }
}
