package com.sentinelcam.node

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sentinelcam.node.receiver.WatchdogReceiver
import com.sentinelcam.node.worker.WatchdogWorker
import java.util.concurrent.TimeUnit

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

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL CRASH in thread ${thread.name}: ${throwable.message}", throwable)
            try {
                // Ensure alarm manager schedules immediate resurrection
                WatchdogReceiver.scheduleWatchdog(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling emergency watchdog on crash: ${e.message}")
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
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
    }
}
