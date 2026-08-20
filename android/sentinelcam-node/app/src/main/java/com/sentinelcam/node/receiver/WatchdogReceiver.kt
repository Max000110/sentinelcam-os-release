package com.sentinelcam.node.receiver

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sentinelcam.node.SentinelApplication
import com.sentinelcam.node.service.CctvForegroundService
import com.sentinelcam.node.ui.MainActivity

class WatchdogReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SentinelCam.Watchdog"
        const val ACTION_WATCHDOG_PING = "com.sentinelcam.node.ACTION_WATCHDOG_PING"
        private const val WATCHDOG_INTERVAL_MS = 2 * 60 * 1000L // 2 minutes continuous health check
        private const val RECOVERY_NOTIFICATION_ID = 2002

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
                Log.d(TAG, "Watchdog hardware alarm scheduled for next check in ${WATCHDOG_INTERVAL_MS / 1000}s")
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
        Log.i(TAG, "Watchdog received action: $action. Checking 24x7 service state on Android ${Build.VERSION.RELEASE}...")

        val hasCamera = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (!hasCamera || !hasAudio) {
            Log.w(TAG, "Cannot resurrect service: Camera or Audio permissions missing")
            return
        }

        if (!CctvForegroundService.isRunning) {
            Log.w(TAG, "CctvForegroundService was CLOSED in background! Resurrecting immediately...")
            val serviceIntent = Intent(context, CctvForegroundService::class.java).apply {
                this.action = CctvForegroundService.ACTION_START
            }

            var serviceStarted = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                serviceStarted = true
                Log.i(TAG, "CctvForegroundService resurrected successfully via startForegroundService.")
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService blocked by Android 14/15/16 background restriction: ${e.message}")
            }

            // Fallback for Android 14/15/16 ForegroundServiceStartNotAllowedException
            if (!serviceStarted) {
                try {
                    // 1. Post High-Priority Action Notification
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val mainPendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val notification: Notification = NotificationCompat.Builder(context, SentinelApplication.CHANNEL_ID)
                        .setContentTitle("SentinelCam Node Resurrecting")
                        .setContentText("Restoring 24x7 CCTV stream & background monitoring")
                        .setSmallIcon(android.R.drawable.ic_menu_camera)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setFullScreenIntent(mainPendingIntent, true)
                        .setContentIntent(mainPendingIntent)
                        .setAutoCancel(true)
                        .build()

                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(RECOVERY_NOTIFICATION_ID, notification)

                    // 2. Direct Activity launch
                    context.startActivity(mainIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed fallback activity resurrection: ${e.message}")
                }
            }
        } else {
            Log.d(TAG, "CctvForegroundService is ACTIVE and healthy.")
        }

        // Reschedule next hardware watchdog tick
        scheduleWatchdog(context)
    }
}
