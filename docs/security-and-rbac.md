# SentinelCam: Security, RBAC & Privacy Model

## 1. Role-Based Access Control (RBAC)
- **`OWNER`**: Full unrestricted access, user creation, factory reset, billing.
- **`ADMIN`**: Fleet management, device registration, zone/rule editing, OTA updates.
- **`OPERATOR`**: Live stream viewing, two-way push-to-talk audio, manual recording, incident acknowledgment.
- **`VIEWER`**: Live stream viewing and playback only.
- **`AUDITOR`**: Read-only access to audit logs, diagnostic packages, and system reports.

---

## 2. API & Network Security
- **Authentication:** Password hashing via bcrypt/Argon2id, JWT access tokens (24h expiry), session revocation via Redis/DB `jti` blocklist.
- **Transport Security:** Strict HTTPS and WSS reverse proxy termination with Nginx.
- **WebRTC Security:** End-to-end media encryption via DTLS-SRTP. Ephemeral time-limited Coturn credentials.
- **Device Commands:** Strict allowlist (`RESTART_SERVICE`, `SWITCH_CAMERA`, `TOGGLE_TORCH`, `SET_PRIVACY_MODE`, `SYNC_CONFIG`). No arbitrary shell execution.
