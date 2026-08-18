package com.sentinelcam.node.service

import android.app.Notification
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
import com.google.gson.JsonObject
import com.sentinelcam.node.SentinelApplication
import com.sentinelcam.node.ai.AiObjectDetector
import com.sentinelcam.node.camera.CameraEngine
import com.sentinelcam.node.data.PreferencesManager
import com.sentinelcam.node.face.FaceIntelligenceEngine
import com.sentinelcam.node.recording.SegmentedRecorder
import com.sentinelcam.node.signaling.SignalingClient
import com.sentinelcam.node.webrtc.WebRtcClient
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

class CctvForegroundService : LifecycleService() {
    companion object {
        var isRunning = false
        private const val NOTIFICATION_ID = 1001
    }

    private val TAG = "SentinelCam.Service"
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private lateinit var prefs: PreferencesManager
    private var cameraEngine: CameraEngine? = null
    private var webRtcClient: WebRtcClient? = null
    private var signalingClient: SignalingClient? = null
    private var recorder: SegmentedRecorder? = null
    private var aiDetector: AiObjectDetector? = null
    private var faceEngine: FaceIntelligenceEngine? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = PreferencesManager(this)

        acquireLocks()
        startForegroundNotification()
        initializeEngines()
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SentinelCam:24x7CctvWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "SentinelCam:24x7WifiLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun startForegroundNotification() {
        val notification: Notification = NotificationCompat.Builder(this, SentinelApplication.CHANNEL_ID)
            .setContentTitle("SentinelCam Active")
            .setContentText("24x7 CCTV Node streaming & monitoring")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
        recorder = SegmentedRecorder(this, prefs.deviceId)
        faceEngine = FaceIntelligenceEngine(this)
        aiDetector = AiObjectDetector(this) { detectedObjects ->
            // AI detections processed asynchronously
        }

        cameraEngine = CameraEngine(this, this) { yuvBytes, width, height ->
            aiDetector?.processFrame(yuvBytes, width, height)
        }
        cameraEngine?.startCamera()

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
            }
        )

        signalingClient = SignalingClient(
            serverUrl = prefs.serverUrl,
            deviceId = prefs.deviceId,
            onMessageReceived = { json -> handleSignalingMessage(json) },
            onConnected = { Log.i(TAG, "Signaling connected to VPS") },
            onDisconnected = { Log.w(TAG, "Signaling disconnected from VPS") }
        )
        signalingClient?.connect()
    }

    private fun handleSignalingMessage(json: JsonObject) {
        val type = json.get("type")?.asString ?: return
        when (type) {
            "viewer_joined" -> {
                // Viewer opened camera page -> Start WebRTC session
                val defaultIceServers = listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                )
                webRtcClient?.startSession(defaultIceServers)
            }
            "answer" -> {
                val sdp = json.get("sdp")?.asString
                sdp?.let { webRtcClient?.handleRemoteAnswer(it) }
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
        cameraEngine?.stop()
        webRtcClient?.close()
        signalingClient?.disconnect()

        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
