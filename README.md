# 🛡️ SentinelCam

> **Transform Old Android Phones into 24×7 Resilient CCTV Nodes with VPS WebRTC Streaming (<500ms Latency)**

---

## ⚡ Core Architecture Principles

1. **WebRTC-First (No RTSP / HLS Delays):**
   - Direct glass-to-glass latency of **150–500 ms**.
   - Hardware-accelerated H.264 video encoding on Android.
   - Low-latency browser rendering via native HTML5 `<video>` and `RTCPeerConnection`.

2. **24×7 Android Node Resilience:**
   - Sticky Foreground Service with persistent notification.
   - `PARTIAL_WAKE_LOCK` and high-performance `WifiLock`.
   - Battery optimization whitelist.
   - Auto-recovery watchdog & boot receiver.

3. **Zero-AI On-Device Motion Detection:**
   - Lightweight frame-to-frame YUV luminance difference computation on device.
   - Zero VPS server CPU load during idle monitoring.
   - Instantaneous snapshot uploads and Telegram Bot alerts.

4. **Two-Way Intercom Audio:**
   - Push-to-Talk and open-mic streaming from browser to Android phone speaker with hardware Acoustic Echo Cancellation (AEC).

5. **NAT Traversal with Coturn:**
   - Dynamic time-limited HMAC-SHA1 STUN/TURN credentials for seamless connectivity across 4G/5G mobile networks and strict firewalls.

---

## 📂 System Structure

```
sentinelcam/
├── android/            # Android CCTV Node App (Kotlin, CameraX, WebRTC, Jetpack Compose)
├── backend/            # FastAPI WebRTC Signaling Server, REST API & Redis Presence
├── coturn/             # Coturn STUN/TURN configuration (turnserver.conf)
├── dashboard/          # Next.js 14 Web Dashboard (Real-time player, telemetry, controls)
├── deploy/             # Docker Compose & Nginx Reverse Proxy
└── README.md
```

---

## 🚀 Quick Start Guide

### 1. Launch Backend & Coturn Stack

```bash
# 1. Navigate to backend directory
cd sentinelcam/backend

# 2. Activate virtual environment
source venv/bin/activate

# 3. Start FastAPI Signaling Server & API
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

- REST API Documentation: [http://localhost:8000/docs](http://localhost:8000/docs)
- WebSocket Signaling: `ws://localhost:8000/ws/signaling/{role}/{device_id}`

### 2. Launch Next.js Web Dashboard

```bash
cd sentinelcam/dashboard
npm run dev
```

- Open Dashboard: [http://localhost:3000](http://localhost:3000)

### 3. Run Simulated CCTV Node / Unit Tests

```bash
# Run backend test suite
cd sentinelcam/backend
PYTHONPATH=. ./venv/bin/pytest tests/test_backend.py -v

# Run simulated node runner (emulates camera connection & telemetry)
python mock_node_runner.py
```

---

## 📱 Android Phone Setup Recommendations (24×7 Operation)

1. **Power Supply & Battery Longevity:**
   - Use a smart plug or timer to keep the phone battery between 20% and 80% to avoid thermal battery bloating.
   - If available, enable *Protect Battery / Bypass Charging* in Android Settings.
2. **Mounting & Thermal Management:**
   - Place the phone in a well-ventilated location away from direct sunlight.
   - SentinelCam automatically monitors device temperature and can step down resolution if $T > 42^\circ\text{C}$.
3. **Permissions:**
   - Grant Camera, Microphone, and disable Battery Optimization when prompted in the app.

---

## 🔒 Security & Ports

For full remote access over cellular/VPS, open the following ports on your VPS firewall:

| Port | Protocol | Purpose |
| :--- | :--- | :--- |
| `80 / 443` | TCP | Web Dashboard & REST/WebSocket Signaling |
| `3478` | UDP/TCP | Coturn STUN/TURN Traversal |
| `5349` | UDP/TCP | Coturn TURNS (TLS) |
| `49152 - 65535` | UDP | WebRTC Media Relay UDP Range |
