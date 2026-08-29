package com.sentinelcam.node

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sentinelcam.node.receiver.WatchdogReceiver
import com.sentinelcam.node.ui.MainActivity
import com.sentinelcam.node.worker.WatchdogWorker
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class SentinelApplication : Application() {
    companion object {
        private const val TAG = "SentinelCam.App"
        const val CHANNEL_ID = "sentinelcam_cctv_channel"
        const val CHANNEL_NAME = "SentinelCam 24x7 CCTV Service"
    }

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        createNotificationChannel()
        scheduleWorkManagerWatchdog()
        WatchdogReceiver.scheduleWatchdog(this)
    }

    /**
     * Bulletproof Crash-to-Relaunch Engine:
     * When any unexpected thread crash occurs in WebRTC, CameraX, OkHttp, or AI engines,
     * this schedules an immediate 1-second alarm wakeup and cleanly restarts the app process.
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL UNCAUGHT EXCEPTION in thread '${thread.name}': ${throwable.message}", throwable)
            try {
                // 1. Write crash details to disk
                val crashFile = File(filesDir, "last_crash.log")
                crashFile.writeText("Crash in thread [${thread.name}] at ${System.currentTimeMillis()}:\n${Log.getStackTraceString(throwable)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing crash log: ${e.message}")
            }

            try {
                // 2. Schedule immediate auto-restart via AlarmManager (1 second delayed wakeup)
                val restartIntent = Intent(this, MainActivity::class.java).apply {
                    action = "com.sentinelcam.node.ACTION_AUTO_RESTART_AFTER_CRASH"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("CRASH_RESTART", true)
                }

                val pendingIntent = PendingIntent.getActivity(
                    this,
                    7777,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )

                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 1000L,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 1000L,
                        pendingIntent
                    )
                }
                Log.i(TAG, "Emergency Auto-Restart scheduled in 1000ms. Terminating crashed process cleanly.")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling crash restart: ${e.message}")
            }

            // 3. Clean termination to avoid Android crash loop throttling
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps SentinelCam CCTV node actively capturing, streaming, and recording 24x7"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun scheduleWorkManagerWatchdog() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val watchdogWork = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "SentinelWatchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                watchdogWork
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling WorkManager watchdog: ${e.message}")
        }
    }
}
