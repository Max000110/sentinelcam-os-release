package com.sentinelcam.node

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sentinelcam.node.worker.WatchdogWorker
import java.util.concurrent.TimeUnit

class SentinelApplication : Application() {
    companion object {
        const val CHANNEL_ID = "sentinelcam_cctv_channel"
        const val CHANNEL_NAME = "SentinelCam 24x7 CCTV Service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleWatchdog()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SentinelCam CCTV node actively capturing, streaming, and recording 24x7"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun scheduleWatchdog() {
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
