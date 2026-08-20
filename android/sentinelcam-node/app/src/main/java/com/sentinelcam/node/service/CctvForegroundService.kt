package com.sentinelcam.node.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import com.sentinelcam.node.SentinelApplication
import com.sentinelcam.node.ai.AiObjectDetector
import com.sentinelcam.node.camera.CameraEngine
import com.sentinelcam.node.data.PreferencesManager
import com.sentinelcam.node.face.FaceIntelligenceEngine
import com.sentinelcam.node.receiver.WatchdogReceiver
import com.sentinelcam.node.recording.SegmentedRecorder
import com.sentinelcam.node.signaling.SignalingClient
import com.sentinelcam.node.telemetry.RealTelemetryCollector
import com.sentinelcam.node.ui.MainActivity
import com.sentinelcam.node.webrtc.WebRtcClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

class CctvForegroundService : LifecycleService() {
    companion object {
        const val ACTION_START = "com.sentinelcam.node.action.START"
        const val ACTION_STOP = "com.sentinelcam.node.action.STOP"
        const val ACTION_RESTART = "com.sentinelcam.node.action.RESTART"

        @Volatile var isRunning = false
        private const val NOTIFICATION_ID = 1001
    }

    private val TAG = "SentinelCam.Service"
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private lateinit var prefs: PreferencesManager
    private var cameraEngine: CameraEngine? = null
    private var webRtcClient: WebRtcClient? = null
    private var signalingClient: SignalingClient? = null
    private var telemetryCollector: RealTelemetryCollector? = null
    private var recorder: SegmentedRecorder? = null
    private var aiDetector: AiObjectDetector? = null
    private var faceEngine: FaceIntelligenceEngine? = null

    @Volatile private var isExplicitlyStopped = false
    private var areEnginesInitialized = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CctvForegroundService onCreate called")
        isRunning = true
        isExplicitlyStopped = false
        prefs = PreferencesManager(this)
        NodeStateHolder.updateState(NodeState.STARTING)

        acquireLocks()
        startForegroundNotification()
        initializeEngines()
        WatchdogReceiver.scheduleWatchdog(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: ACTION_START
        Log.i(TAG, "CctvForegroundService onStartCommand action: $action (startId: $startId)")

        if (action == ACTION_STOP) {
            Log.i(TAG, "Explicit stop requested by user")
            isExplicitlyStopped = true
            WatchdogReceiver.cancelWatchdog(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        isExplicitlyStopped = false
        isRunning = true

        // Re-verify locks and notification on every startCommand invocation
        acquireLocks()
        startForegroundNotification()

        if (!areEnginesInitialized) {
            initializeEngines()
        }

        // Schedule periodic watchdog to guarantee self-healing
        WatchdogReceiver.scheduleWatchdog(this)

        // START_STICKY tells Android OS: if killed by memory pressure, restart service automatically
        return START_STICKY
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null || !wakeLock!!.isHeld) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SentinelCam:24x7CctvWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire() // Indefinite acquisition - 24/7 CCTV operation
                }
                Log.i(TAG, "Acquired indefinite Partial WakeLock")
            }

            if (wifiLock == null || !wifiLock!!.isHeld) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifiManager.createWifiLock(
                    lockType,
                    "SentinelCam:24x7WifiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.i(TAG, "Acquired low-latency Wi-Fi Lock")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring power/wifi locks: ${e.message}", e)
        }
    }

    private fun startForegroundNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CctvForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, SentinelApplication.CHANNEL_ID)
            .setContentTitle("SentinelCam Node (24x7 Active)")
            .setContentText("Camera stream & WebRTC signaling live in background")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initializeEngines() {
        if (areEnginesInitialized) return
        areEnginesInitialized = true

        recorder = SegmentedRecorder(this, prefs.deviceId)
        faceEngine = FaceIntelligenceEngine(this)
        aiDetector = AiObjectDetector { _ -> }

        // 1. Initialize WebRTC Client
        webRtcClient = WebRtcClient(
            context = this,
            onIceCandidateGenerated = { candidate ->
                val candJson = JsonObject().apply {
                    addProperty("sdpMid", candidate.sdpMid)
                    addProperty("sdpMLineIndex", candidate.sdpMLineIndex)
                    addProperty("candidate", candidate.sdp)
                }
                val payload = JsonObject().apply { add("candidate", candJson) }
                signalingClient?.sendMessage("ice_candidate", payload)
            },
            onOfferGenerated = { offer ->
                val payload = JsonObject().apply { addProperty("sdp", offer.description) }
                signalingClient?.sendMessage("offer", payload)
                NodeStateHolder.updateState(NodeState.STREAMING)
            }
        )

        // 2. Initialize CameraX Engine
        cameraEngine = CameraEngine(this, this) { nv21Bytes, width, height, rotation ->
            webRtcClient?.onFrameCaptured(nv21Bytes, width, height, rotation)
            NodeStateHolder.updateFps(cameraEngine?.activeFps ?: 30)
        }
        cameraEngine?.startCamera()

        // AI background sampling decoupled from camera thread
        val aiThread = Thread({
            while (CctvForegroundService.isRunning) {
                try {
                    Thread.sleep(500) // ~2 FPS AI sampling rate
                } catch (e: InterruptedException) {
                    break
                }
            }
        }, "SentinelCam-AI-Sampler")
        aiThread.isDaemon = true
        aiThread.start()

        // 3. Initialize Real Telemetry Collector
        telemetryCollector = RealTelemetryCollector(
            context = this,
            serverUrl = prefs.serverUrl,
            deviceId = prefs.deviceId,
            getFpsProvider = { cameraEngine?.activeFps ?: 30 }
        )
        telemetryCollector?.start(intervalSeconds = 10)

        lifecycleScope.launch(Dispatchers.IO) {
            val regSuccess = telemetryCollector?.registerDeviceSync() ?: false
            if (regSuccess) {
                NodeStateHolder.updateApiStatus("CONNECTED")
                NodeStateHolder.updateState(NodeState.REGISTERED)
                NodeStateHolder.recordSuccess("Registered device with backend")
            } else {
                NodeStateHolder.updateApiStatus("ERROR")
            }
        }

        // 4. Initialize WebSocket Signaling Client
        signalingClient = SignalingClient(
            serverUrl = prefs.serverUrl,
            deviceId = prefs.deviceId,
            onMessageReceived = { json -> handleSignalingMessage(json) },
            onConnected = {
                Log.i(TAG, "Signaling connected to VPS")
                NodeStateHolder.updateWsStatus("CONNECTED")
                NodeStateHolder.updateState(NodeState.WEBSOCKET_CONNECTED)
                NodeStateHolder.recordSuccess("Signaling WebSocket connected")
            },
            onDisconnected = {
                Log.w(TAG, "Signaling disconnected from VPS")
                NodeStateHolder.updateWsStatus("DISCONNECTED")
                NodeStateHolder.updateState(NodeState.DISCONNECTED)
            }
        )
        signalingClient?.connect()
    }

    private fun handleSignalingMessage(json: JsonObject) {
        val type = json.get("type")?.asString ?: return
        when (type) {
            "viewer_joined" -> {
                val iceServersList = mutableListOf<PeerConnection.IceServer>()
                try {
                    val iceServersJson = json.getAsJsonArray("ice_servers")
                    if (iceServersJson != null && iceServersJson.size() > 0) {
                        for (i in 0 until iceServersJson.size()) {
                            val serverObj = iceServersJson.get(i).asJsonObject
                            val urlsArray = serverObj.getAsJsonArray("urls")
                            val username = serverObj.get("username")?.asString
                            val credential = serverObj.get("credential")?.asString
                            val urls = mutableListOf<String>()
                            urlsArray?.forEach { urls.add(it.asString) }
                            if (urls.isNotEmpty()) {
                                val builder = PeerConnection.IceServer.builder(urls)
                                if (!username.isNullOrEmpty() && !credential.isNullOrEmpty()) {
                                    builder.setUsername(username)
                                    builder.setPassword(credential)
                                }
                                iceServersList.add(builder.createIceServer())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing ice_servers from viewer_joined: ${e.message}")
                }
                if (iceServersList.isEmpty()) {
                    iceServersList.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
                    iceServersList.add(PeerConnection.IceServer.builder("stun:161.118.183.23:3478").createIceServer())
                }
                Log.i(TAG, "Starting WebRTC session with ${iceServersList.size} ICE servers")
                NodeStateHolder.updateRtcStatus("CONNECTING")
                webRtcClient?.startSession(iceServersList)
            }
            "answer" -> {
                val sdp = json.get("sdp")?.asString
                sdp?.let {
                    webRtcClient?.handleRemoteAnswer(it)
                    NodeStateHolder.updateRtcStatus("STREAMING")
                    NodeStateHolder.updateState(NodeState.STREAMING)
                }
            }
            "ice_candidate" -> {
                val cand = json.getAsJsonObject("candidate")
                if (cand != null) {
                    val iceCandidate = IceCandidate(
                        cand.get("sdpMid")?.asString,
                        cand.get("sdpMLineIndex")?.asInt ?: 0,
                        cand.get("candidate")?.asString
                    )
                    webRtcClient?.addRemoteIceCandidate(iceCandidate)
                }
            }
            "command" -> {
                val cmd = json.get("command")?.asString
                when (cmd) {
                    "switch_camera" -> cameraEngine?.switchCamera()
                    "toggle_torch" -> cameraEngine?.toggleTorch()
                    "set_privacy_mode" -> {
                        val enabled = json.get("payload")?.asJsonObject?.get("enabled")?.asBoolean ?: false
                        faceEngine?.setPrivacyMode(enabled)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        areEnginesInitialized = false
        NodeStateHolder.updateState(NodeState.STOPPED)
        NodeStateHolder.updateApiStatus("STOPPED")
        NodeStateHolder.updateWsStatus("STOPPED")
        NodeStateHolder.updateRtcStatus("STOPPED")

        telemetryCollector?.stop()
        cameraEngine?.stop()
        webRtcClient?.close()
        signalingClient?.disconnect()
        aiDetector?.shutdown()
        recorder?.shutdown()

        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }

        if (!isExplicitlyStopped) {
            Log.w(TAG, "CctvForegroundService destroyed UNEXPECTEDLY by Android OS! Scheduling immediate recovery...")
            WatchdogReceiver.scheduleWatchdog(applicationContext)
        } else {
            Log.i(TAG, "CctvForegroundService stopped cleanly by user")
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
