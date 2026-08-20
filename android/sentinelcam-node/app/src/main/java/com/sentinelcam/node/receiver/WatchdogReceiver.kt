package com.sentinelcam.node.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.sentinelcam.node.service.CctvForegroundService

class WatchdogReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SentinelCam.Watchdog"
        const val ACTION_WATCHDOG_PING = "com.sentinelcam.node.ACTION_WATCHDOG_PING"
        const val ACTION_RESTART_SERVICE = "com.sentinelcam.node.ACTION_RESTART_SERVICE"
        private const val WATCHDOG_INTERVAL_MS = 3 * 60 * 1000L // 3 minutes

        fun scheduleWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, WatchdogReceiver::class.java).apply {
                    action = ACTION_WATCHDOG_PING
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerTime = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Watchdog alarm scheduled for next check in ${WATCHDOG_INTERVAL_MS / 1000}s")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling watchdog alarm: ${e.message}")
            }
        }

        fun cancelWatchdog(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, WatchdogReceiver::class.java).apply {
                    action = ACTION_WATCHDOG_PING
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9001,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling watchdog: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Watchdog received action: $action. Checking service liveness...")

        val hasCamera = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (!hasCamera || !hasAudio) {
            Log.w(TAG, "Cannot start service: Camera or Audio permissions missing")
            return
        }

        if (!CctvForegroundService.isRunning) {
            Log.w(TAG, "CctvForegroundService is NOT running! Resurrecting 24x7 service immediately...")
            val serviceIntent = Intent(context, CctvForegroundService::class.java).apply {
                this.action = CctvForegroundService.ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service from watchdog: ${e.message}", e)
            }
        } else {
            Log.i(TAG, "CctvForegroundService is ACTIVE and healthy.")
        }

        // Reschedule next watchdog tick
        scheduleWatchdog(context)
    }
}
