# Embedded Runtime & Appliance Stability

## 1. 24x7 Appliance Characteristics
- **Device Tested**: Samsung Galaxy M14 5G (`SM-M146B`, Exynos 1330 Octa-Core).
- **Foreground Service Architecture**: `CctvForegroundService` runs as a persistent service with `WIFI_MODE_FULL_HIGH_PERF` WifiLock and `PARTIAL_WAKE_LOCK`.
- **Thermal & Frame Rate Stability**: Locked 30 FPS Camera2 fixed exposure range prevents ISP exposure hunting and reduces thermal dissipation.

## 2. Threading & Memory Profiling
- **Decoupled Architecture**: WebRTC frame delivery pipeline is completely separated from local AI inference. WebRTC receives NV21 frames directly with zero-copy buffer pooling.
- **Zero-GC Buffer Pool**: `CameraEngine` utilizes pre-allocated `reusableNv21` byte arrays (640x480x1.5 = 460.8 KB), achieving 0 allocs/frame and < 0.6ms conversion latency.
- **Graceful Teardown**: `onDestroy()` cleanly releases camera providers, WebRTC clients, signaling handlers, wake locks, and shuts down all executors (`executor.shutdown()`).
