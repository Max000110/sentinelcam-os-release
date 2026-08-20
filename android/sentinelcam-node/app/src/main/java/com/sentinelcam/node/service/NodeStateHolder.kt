package com.sentinelcam.node.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NodeState {
    STOPPED,
    STARTING,
    CONNECTING,
    API_CONNECTED,
    REGISTERED,
    WEBSOCKET_CONNECTED,
    STREAMING,
    DISCONNECTING,
    DISCONNECTED,
    RECONNECTING,
    ERROR
}

object NodeStateHolder {
    private val _connectionState = MutableStateFlow(NodeState.STOPPED)
    val connectionState: StateFlow<NodeState> = _connectionState.asStateFlow()

    private val _apiStatus = MutableStateFlow("STOPPED")
    val apiStatus: StateFlow<String> = _apiStatus.asStateFlow()

    private val _wsStatus = MutableStateFlow("STOPPED")
    val wsStatus: StateFlow<String> = _wsStatus.asStateFlow()

    private val _rtcStatus = MutableStateFlow("STOPPED")
    val rtcStatus: StateFlow<String> = _rtcStatus.asStateFlow()

    private val _lastError = MutableStateFlow("None")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    private val _lastSuccess = MutableStateFlow("Never")
    val lastSuccess: StateFlow<String> = _lastSuccess.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    fun updateState(state: NodeState) {
        _connectionState.value = state
    }

    fun updateApiStatus(status: String) {
        _apiStatus.value = status
    }

    fun updateWsStatus(status: String) {
        _wsStatus.value = status
    }

    fun updateRtcStatus(status: String) {
        _rtcStatus.value = status
    }

    fun updateFps(currentFps: Int) {
        _fps.value = currentFps
    }

    fun recordSuccess(msg: String) {
        _lastSuccess.value = msg
        _lastError.value = "None"
    }

    fun recordError(err: String) {
        _lastError.value = err
        _connectionState.value = NodeState.ERROR
    }
}
