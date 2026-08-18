package com.sentinelcam.node.signaling

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

class SignalingClient(
    private val serverUrl: String,
    private val deviceId: String,
    private val onMessageReceived: (JsonObject) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit
) {
    private val TAG = "SentinelCam.Signaling"
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var reconnectAttempt = 0
    private var isClosedManually = false

    fun connect() {
        isClosedManually = false
        val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://") +
                "/ws/signaling/node/$deviceId"

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Signaling WebSocket connected to $wsUrl")
                reconnectAttempt = 0
                mainHandler.post { onConnected() }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, JsonObject::class.java)
                    mainHandler.post { onMessageReceived(json) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing signaling message: ${e.message}")
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Signaling WebSocket closing: $reason")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Signaling WebSocket closed")
                mainHandler.post { onDisconnected() }
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Signaling WebSocket failure: ${t.message}")
                mainHandler.post { onDisconnected() }
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (isClosedManually) return
        reconnectAttempt++
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, max 30s
        val delayMs = min(30000L, (1L shl min(reconnectAttempt, 5)) * 1000L)
        Log.i(TAG, "Scheduling reconnect attempt #$reconnectAttempt in ${delayMs}ms")
        mainHandler.postDelayed({ connect() }, delayMs)
    }

    fun sendMessage(type: String, data: JsonObject = JsonObject()) {
        data.addProperty("type", type)
        data.addProperty("device_id", deviceId)
        data.addProperty("sender_role", "node")
        webSocket?.send(gson.toJson(data))
    }

    fun disconnect() {
        isClosedManually = true
        webSocket?.close(1000, "Node disconnecting normally")
        webSocket = null
    }
}
