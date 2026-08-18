# Architecture

## High-Level Architecture
```
┌──────────────────────────────────────┐
│ Android CCTV Node                    │
│--------------------------------------│
│ CameraX                              │
│ Audio Capture                        │
│ Motion Detection                     │
│ Foreground Service                   │
│ WebRTC Publisher                     │
│ Local Recorder                       │
└──────────────────────────────────────┘
                   │
                   │ (Signaling via WebSocket)
                   ▼
┌──────────────────────────────────────┐
│ VPS Backend / Control Plane          │
│--------------------------------------│
│ FastAPI (Signaling Server)           │
│ Redis (Pub/Sub & Presence)           │
│ PostgreSQL (Users & Auth)            │
│ Coturn (STUN/TURN)                   │
└──────────────────────────────────────┘
                   │
                   │ (Signaling via WebSocket)
                   ▼
┌──────────────────────────────────────┐
│ Web Dashboard                        │
│--------------------------------------│
│ Next.js                              │
│ WebRTC Viewer                        │
│ Device Management                    │
└──────────────────────────────────────┘
```

## Core Design Principles
1. **WebRTC-First**: To achieve 150-500ms latency, the system strictly utilizes WebRTC over alternatives like HLS, DASH, or MJPEG.
2. **Privacy**: Biometric consent and secure JWT authorization protect devices.
3. **Resilience**: The Android app runs as a foreground service to ensure constant availability.
