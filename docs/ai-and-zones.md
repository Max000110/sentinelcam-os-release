# SentinelCam: AI Detection, Zones & Facial Intelligence

## 1. On-Device Edge AI Pipeline
- **Sampling Strategy:** 3 FPS baseline on CameraX frame buffer (downscaled to 320x320 for TFLite).
- **Thermal Adaptation:** Dynamically steps down from 3 FPS -> 2 FPS at 38°C, 1 FPS at 42°C, and pauses AI if temperature > 45°C (preserving live WebRTC streaming and device battery health).
- **Object Classes:** `person`, `car`, `motorcycle`, `bus`, `truck`, `bicycle`.
- **Tracking:** IoU-based tracking assigning persistent `track_id` across frames.

---

## 2. Polygonal Zones & Tripwires
- **Polygons:** Users define normalized $(x, y) \in [0.0, 1.0]$ vertices.
- **Zone Types:**
  - `PROTECTED`: Elevates severity to `HIGH`/`CRITICAL` and dispatches alerts.
  - `IGNORED`: Suppresses motion noise (e.g. moving curtains, tree branches, ceiling fans).
  - `MONITOR`: Logs detection history for analytics.
- **Tripwires:** Line crossing detection between point $A(x_1, y_1)$ and point $B(x_2, y_2)$ with direction enforcement ($A \to B$, $B \to A$, or $ANY$).

---

## 3. Consent-Based Face Recognition
- **Architecture:** Local-first embedding comparison using cosine similarity against opt-in enrolled biometric templates.
- **Privacy Mode:** One-tap toggle disables all facial embedding generation and AI overlays immediately.
- **Encryption:** Biometric vectors stored using AES-256 with Android Keystore hardware-backed keys.
