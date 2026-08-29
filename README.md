# 🛡️ SentinelCam OS

> **Transform Android Phones into Ultra-Low-Latency (<35ms), 24×7 Resilient AI CCTV Security Nodes with WebRTC, DirectBoot Auto-Restart, and OLED Zero-Power Mode.**

[![GitHub Release](https://img.shields.io/github/v/release/Max000110/sentinelcam-os-release?style=for-the-badge&color=00E676)](https://github.com/Max000110/sentinelcam-os-release/releases)
[![Android](https://img.shields.io/badge/Android-14%20%7C%2015%20%7C%2016%20Preview-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Max000110/sentinelcam-os-release)
[![WebRTC](https://img.shields.io/badge/WebRTC-Glass--to--Glass%20%3C35ms-FF6F00?style=for-the-badge&logo=webrtc&logoColor=white)](https://webrtc.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.110%2B-009688?style=for-the-badge&logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com)
[![Next.js](https://img.shields.io/badge/Next.js-14.2%20App%20Router-black?style=for-the-badge&logo=next.js&logoColor=white)](https://nextjs.org/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

---

## 📑 Table of Contents
- [✨ Key Capabilities](#-key-capabilities)
- [🏛️ System Architecture](#️-system-architecture)
- [📱 Android CCTV Node Features](#-android-cctv-node-features)
- [💻 Web Management Dashboard](#-web-management-dashboard)
- [⚡ Backend & Signaling Infrastructure](#-backend--signaling-infrastructure)
- [🛡️ OEM 24/7 Survival Guide (Vivo, Samsung, Xiaomi)](#️-oem-247-survival-guide-vivo-samsung-xiaomi)
- [🚀 Quick Start & Deployment](#-quick-start--deployment)
- [📦 Download Production Release APKs](#-download-production-release-apks)
- [🔒 Security, Network Ports & Firewall](#-security-network-ports--firewall)
- [📜 Release History & Changelog](#-release-history--changelog)

---

## ✨ Key Capabilities

- ⚡ **Ultra-Low Latency (<35ms Glass-to-Glass)**: Hardware-accelerated H.264 baseline encoding piped directly through WebRTC peer connections. Zero RTSP/HLS buffering delays.
- 🔄 **DirectBoot Auto-Start on Phone Reboot**: Starts capturing and streaming automatically when the phone powers on or reboots—**even before the lock screen is unlocked**.
- 🛡️ **Self-Healing Crash Resurrection Engine**: Catches any unhandled thread exceptions and automatically relaunches the node within 1,000ms via hardware `AlarmManager` wakeup.
- 📺 **OLED 0% Backlight Power Mode**: Keeps the camera sensor active 24/7 while dimming the screen to pitch-black (`#000000`) at near-zero power draw, preventing Android HAL camera sleep.
- 🗣️ **Two-Way Intercom**: Real-time push-to-talk audio streaming with hardware Acoustic Echo Cancellation (AEC) and Noise Suppression (NS).
- 🚨 **Edge Motion Detection & Snapshots**: Zero server CPU load motion analysis on-device with automatic snapshot uploads and Telegram bot alerts.
- 🌐 **Cellular & NAT Traversal**: Dynamic time-limited HMAC-SHA1 STUN/TURN tokens (CoTURN) for smooth streaming over 4G/5G mobile data and carrier CGNAT.

---

## 🏛️ System Architecture

```mermaid
graph LR
    subgraph "Android CCTV Node (Phone)"
        CAM["CameraX / Camera2 HAL\n(30 FPS Fixed)"]
        H264["Hardware H.264 Encoder"]
        RTC_N["libwebrtc Native Engine"]
        FGS["Foreground Service\n(specialUse + directBoot)"]
        ALARM["Hardware AlarmManager\n(2-Min Watchdog)"]
        CAM --> H264 --> RTC_N
        FGS -.-> CAM
        ALARM -.-> FGS
    end

    subgraph "VPS Server"
        SIG["FastAPI WebSocket Signaling\n(:8000)"]
        API["REST API & Auth\n(:8000)"]
        TURN["CoTURN Server\n(:3478 / UDP 49152-65535)"]
        PG[("PostgreSQL 16")]
        REDIS[("Redis 7 Cache")]
        SIG <---> API
        API <---> PG
        API <---> REDIS
    end

    subgraph "Web Browser (Dashboard)"
        DASH["Next.js 14 Web App\n(:3000)"]
        RTC_V["HTML5 Video / RTCPeerConnection"]
        DASH --> RTC_V
    end

    RTC_N <== "Encrypted WebRTC Media (SRTP/DTLS) <35ms" ==> RTC_V
    RTC_N <--> "Signaling (JSON / WS)" <--> SIG
    RTC_V <--> "Signaling (JSON / WS)" <--> SIG
    RTC_N -. "ICE Candidate Relay" .-> TURN
    RTC_V -. "ICE Candidate Relay" .-> TURN
```

---

## 📱 Android CCTV Node Features

The SentinelCam Android Node (`android/sentinelcam-node`) is designed for 24×7 uninterrupted operation on modern Android devices (Android 14, 15, and 16 Preview).

- **Android 14/15/16 FGS Compliance**: Configured with official `specialUse` FGS types and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` to satisfy strict background execution policies.
- **DirectBoot Support (`android:directBootAware="true"`)**: Utilizes `createDeviceProtectedStorageContext()` to load device preferences, connect to signaling, and stream before PIN entry.
- **Dual Lock System**: Combines indefinite `PowerManager.PARTIAL_WAKE_LOCK` and low-latency `WifiManager.WIFI_MODE_FULL_LOW_LATENCY`.
- **Camera2 ISP Tuning**:
  - Locked 30 FPS fixed capture range (`CONTROL_AE_TARGET_FPS_RANGE = [30, 30]`).
  - Optical & Video stabilization disabled to eliminate ISP buffer lag (~100ms savings).
  - Fast single-pass noise reduction and edge enhancement.
- **Resilient Signaling Client**: Synchronized remote ICE candidate queueing ensures candidates arriving before SDP offer/answer completion are buffered and flushed seamlessly.

---

## 💻 Web Management Dashboard

Built with **Next.js 14 App Router**, **TypeScript**, and **Tailwind CSS**.

- 📺 **Live WebRTC Grid & Single Camera Player**: Adaptive bitrate streaming with real-time jitter buffer tuning (`playoutDelayHint = 0`).
- 🎮 **Hardware Remote Controls**:
  - Torch / Flashlight toggle
  - Front / Rear lens switching
  - Privacy Mode toggle
- 📊 **Authoritative Telemetry Panel**:
  - Live Glass-to-Glass Latency measurement (ms)
  - Sensor FPS, CPU utilization, and thermal temperature
  - Battery charge level and Wi-Fi RSSI signal strength
- 🎙️ **Two-Way Intercom**: Browser microphone capture with Opus audio codec encoding.
- 🎞️ **Segmented Video Recordings & Motion Incident Timeline**.

---

## ⚡ Backend & Signaling Infrastructure

- **FastAPI Core (`backend/app`)**: Asynchronous REST API, JWT authentication, and WebSocket signaling router.
- **Presence & State Management**: Redis-backed device heartbeat registry with automatic offline detection.
- **CoTURN Integration**: Ephemeral HMAC-SHA1 TURN credential generation (`turn_service.py`) for dynamic ICE configurations.
- **Database Storage**: SQLAlchemy 2.0 async engine connected to PostgreSQL 16 for device records, security logs, and motion events.

---

## 🛡️ OEM 24/7 Survival Guide (Vivo, Samsung, Xiaomi)

Modern Android OEM skins (Vivo Funtouch OS, Samsung One UI, Xiaomi HyperOS) contain aggressive power management daemons that terminate background applications. Follow these steps for 100% 24/7 reliability:

### 1. Vivo & iQOO Devices (e.g., Vivo V40 5G, Funtouch OS 14)
1. **Enable Auto-Start**: Open **SentinelCam Node** ➡️ Tap **`1. Auto-Start`** ➡️ In Vivo iManager, enable **SentinelCam Node**.
2. **Enable High Background Power**: Tap **`2. High Power`** ➡️ Select **High Background Power Consumption** (or Unrestricted).
3. **Lock in Recent Apps**: Open Android Recent Apps ➡️ Swipe down on SentinelCam card ➡️ Tap the **Padlock icon (🔒)**.
4. **24/7 Capture Mode**: Tap **Start Service** ➡️ Tap **OLED Black Screen**. (The AMOLED screen turns black with 0% backlight while the camera captures continuously).

### 2. Samsung Galaxy Devices (One UI 6+ / Android 14)
1. **Battery Settings**: Go to **Settings** ➡️ **Apps** ➡️ **SentinelCam Node** ➡️ **Battery** ➡️ Choose **Unrestricted**.
2. **Never Sleeping Apps**: Go to **Settings** ➡️ **Battery** ➡️ **Background usage limits** ➡️ Add SentinelCam Node to **Never sleeping apps**.
3. **Auto-Restart on Reboot**: Ensure **"Auto-Start on Boot / Reboot"** is enabled inside the app.

---

## 🚀 Quick Start & Deployment

### Method A: 1-Command Docker Deployment (VPS)

1. Clone the repository on your VPS:
   ```bash
   git clone https://github.com/Max000110/sentinelcam-os-release.git /home/ubuntu/sentinelcam
   cd /home/ubuntu/sentinelcam/deploy
   ```

2. Configure environment variables:
   ```bash
   cp .env.example .env
   # Edit VPS_PUBLIC_IP, POSTGRES_PASSWORD, COTURN_STATIC_AUTH_SECRET
   nano .env
   ```

3. Launch the complete stack:
   ```bash
   docker compose up -d --build
   ```

4. Access services:
   - **Web Dashboard**: `http://<YOUR_VPS_IP>:3000`
   - **REST API & Swagger Docs**: `http://<YOUR_VPS_IP>:8000/docs`
   - **WebSocket Signaling**: `ws://<YOUR_VPS_IP>:8000/ws/signaling/{role}/{device_id}`

---

### Method B: Local Development Setup

#### Backend (Python 3.11+)
```bash
cd sentinelcam/backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

#### Dashboard (Node.js 18+)
```bash
cd sentinelcam/dashboard
npm install
npm run dev
```

#### Android Node (Android Studio / Gradle)
```bash
cd sentinelcam/android/sentinelcam-node
./gradlew assembleRelease
```

---

## 📦 Download Production Release APKs

All signed production APKs are published with SHA-256 verification checksums:

| Release | Android Target | Key Enhancements | Download |
| :--- | :--- | :--- | :--- |
| **v2.2.0** *(Latest)* | Android 14 / 15 / 16 | Vivo V40 5G crash recovery, DirectBoot device-protected storage, OEM whitelist shortcuts | [Download APK](https://github.com/Max000110/sentinelcam-os-release/releases/download/v2.2.0/sentinelcam-v2.2.0-release.apk) |
| **v2.1.1** | Android 14 / 15 / 16 | ICE candidate queueing & WebRTC handshake race condition fixes | [Download APK](https://github.com/Max000110/sentinelcam-os-release/releases/download/v2.1.1/sentinelcam-v2.1.1-release.apk) |
| **v2.1.0** | Android 14 / 15 / 16 | OLED 0% Black Screen Mode, DirectBoot auto-start on reboot | [Download APK](https://github.com/Max000110/sentinelcam-os-release/releases/download/v2.1.0/sentinelcam-v2.1.0-release.apk) |
| **v2.0.0** | Android 14 / 15 | Baseline WebRTC production release | [Download APK](https://github.com/Max000110/sentinelcam-os-release/releases/download/v2.0.0/sentinelcam-v2.0.0-release.apk) |

---

## 🔒 Security, Network Ports & Firewall

For unrestricted WebRTC streaming across cellular carriers and firewalls, configure these ports on your server:

| Port Range | Protocol | Service | Description |
| :--- | :--- | :--- | :--- |
| `80 / 443` | TCP | Nginx / HTTP(S) | Web Dashboard & REST/WebSocket Signaling |
| `3000` | TCP | Next.js Dashboard | Web Dashboard direct port |
| `8000` | TCP | FastAPI Backend | API & WebSocket signaling port |
| `3478` | UDP/TCP | CoTURN STUN/TURN | NAT Traversal port |
| `5349` | UDP/TCP | CoTURN TURNS | Secure TLS TURN port |
| `49152 - 65535` | UDP | CoTURN Media Relay | WebRTC PeerConnection media UDP relay range |

---

## 📜 Release History & Changelog

### [v2.2.0] — 2026-08-29
- **Fixed**: DirectBoot `KeyStoreException` on Vivo V40 5G using `createDeviceProtectedStorageContext()`.
- **Added**: Self-Healing Crash Resurrection Engine (1-second restart on uncaught exceptions).
- **Added**: Dedicated Vivo / Funtouch OS Auto-Start & High Power Consumption whitelist helpers in the UI.
- **Fixed**: Android 14 While-in-Use background `CAMERA` FGS start restrictions.

### [v2.1.1] — 2026-08-21
- **Fixed**: WebRTC remote ICE candidate race condition in both Next.js frontend and Android node.
- **Fixed**: Upgraded deprecated `MasterKeys` to modern AndroidX `MasterKey.Builder`.
- **Optimized**: Signaling and telemetry candidate URL priority routing.

### [v2.1.0] — 2026-08-20
- **Added**: OLED 0% Backlight Black Screen Mode with double-tap exit for 24/7 continuous stream.
- **Added**: DirectBoot `LOCKED_BOOT_COMPLETED` auto-start on device reboot.
- **Added**: `specialUse` Foreground Service classification for Android 14/15/16.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
