package com.sentinelcam.node.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sentinelcam.node.service.CctvForegroundService

class WatchdogWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i("SentinelCam.Watchdog", "Watchdog checking 24x7 ForegroundService liveness...")
        if (!CctvForegroundService.isRunning) {
            Log.w("SentinelCam.Watchdog", "CctvForegroundService is DEAD. Relaunching...")
            val intent = Intent(context, CctvForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        return Result.success()
    }
}
