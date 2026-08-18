# SentinelCam: Architecture & Technical Specification

## 1. System Overview

SentinelCam transforms repurposed Android smartphones into resilient, 24×7 CCTV nodes streaming glass-to-glass ultra-low-latency video (150–500 ms) via WebRTC to a remote VPS and Next.js Web Dashboard.

```
+-------------------------------------------------------------------------+
|                              INTERNET                                   |
|                                 |                                       |
|                   +-------------+-------------+                         |
|                   |                           |                         |
|                   v                           v                         |
|             Web Dashboard               Android Fleet                   |
|                   |                           |                         |
|                   | HTTPS/WSS                 | WebRTC/WSS              |
|                   |                           |                         |
|             +-----v------+              +-----v------+                  |
|             |   NGINX    |              | Device     |                  |
|             | TLS/WAF    |              | Runtime    |                  |
|             +-----T------+              +-----T------+                  |
|                   |                           |                         |
|            +------v--------+           +------v--------+                |
|            |               |           |               |                |
|            v               v           v               v                |
|         FastAPI         WebSocket    Camera        AI Engine            |
|         REST API         Control     CameraX        TFLite              |
|            |                           |               |                |
|            +---------------+-----------+---------------+                |
|                            |                                            |
|                     +------v------+                                     |
|                     | Redis       |                                     |
|                     | Presence    |                                     |
|                     | Pub/Sub     |                                     |
|                     +------T------+                                     |
|                            |                                            |
|              +-------------+-------------+                              |
|              v             v             v                              |
|         PostgreSQL     Coturn STUN/TURN Monitoring                      |
|         Storage        UDP/TCP Relay    Observability                   |
+-------------------------------------------------------------------------+
```

---

## 2. Hard Separation of Subsystems

1. **Real-Time Media Path:** WebRTC (H.264 / Opus) direct peer-to-peer or Coturn TURN relay. No transcoding or server-side buffering in the live path.
2. **Recording Path:** Local segmented MP4 recorder on Android node with 10s pre-event and 30s post-event rolling buffers.
3. **AI & Intelligence Path:** Local on-device frame sampler at 3 FPS with thermal adaptation, polygonal zone raycasting, and consent-based face recognition.
4. **Control & Signaling Path:** FastAPI async WebSocket gateway for SDP offers, answers, ICE candidates, and allowlisted remote commands.
5. **Presentation Path:** Next.js 14 dashboard with transparent HTML5 Canvas for real-time bounding boxes and zones without touching the video stream.
