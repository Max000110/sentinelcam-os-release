# SentinelCam Architecture Security Audit & Threat Model

## 1. System Topology & Data Flow
SentinelCam is an enterprise-grade 24x7 Android-to-Cloud-to-Browser CCTV monitoring platform.

```
+-------------------------------------------------------------------------+
|                              PUBLIC INTERNET                             |
+-------------------------------------------------------------------------+
       | (WSS / HTTPS :8000)                               | (HTTPS :3000)
       v                                                   v
+------------------------+                       +------------------------+
|    FastAPI Backend     | <-------------------  |   Next.js Dashboard    |
| (Signaling & REST API) |                       |   (W3C WebRTC Viewer)  |
+------------------------+                       +------------------------+
       |                 |                                   |
       | (Internal)      | (Direct DTLS-SRTP P2P / TURN)     | (DTLS-SRTP)
       v                 v                                   v
+---------------+ +------------------+             +----------------------+
| Postgres 16   | |   Coturn 4.6     | <---------> | Android Camera Node  |
| & Redis 7     | | (STUN/TURN Relay)|             | (Samsung Galaxy M14) |
+---------------+ +------------------+             +----------------------+
```

## 2. Trust Boundaries & Attack Surface Analysis
1. **Public Web Entry Points**:
   - Next.js Dashboard (`:3000`): Operator UI, camera matrix, telemetry dashboard.
   - FastAPI Backend (`:8000`): REST endpoints (`/api/v1/*`) and WebSocket hubs (`/ws/*`).
   - Coturn STUN/TURN (`:3478`, `:5349`): UDP/TCP relay for symmetric NAT traversal.
2. **Private Internal Network**:
   - PostgreSQL 16 (`:5432`): Isolated in Docker internal bridge network. No external ports.
   - Redis 7 (`:6379`): Presence, rate-limiting, and ephemeral state. No external ports.
3. **Hardware & Embedded Boundary**:
   - Samsung Galaxy M14 5G (`SM-M146B`, Android 14): CameraX ISP engine, WebRTC HW H.264 encoder, local YUV AI detector.

## 3. STRIDE Threat Modeling

| Threat Category | Component / Flow | Potential Attack Vector | Applied Mitigation |
| :--- | :--- | :--- | :--- |
| **Spoofing** | WebSocket Signaling & Commands | Unauthorized client claiming to be camera node | Role-based WebSocket handshake validation, unique node API keys, and device ID matching. |
| **Tampering** | WebRTC Media & Signaling | In-flight payload or SDP modification | DTLS-SRTP end-to-end cryptographic encryption; strict JSON schema parsing and regex device filtering. |
| **Repudiation** | Hardware commands & incident status | Operator denying actions taken | Comprehensive audit trails with UTC timestamps in `Incident` and system logs. |
| **Information Disclosure** | Face biometrics & Video recordings | Exposing raw biometric embeddings or video files | AES-256-GCM / Fernet encryption at rest for biometric vectors; canonical path traversal validation on video streaming. |
| **Denial of Service** | Video uploads & Telemetry flooding | Exhausting disk or asyncio event loop | 500MB upload limits, 10MB snapshot limits, non-blocking `asyncio.to_thread` password hashing, connection pooling. |
| **Elevation of Privilege** | API access control | Regular operator performing admin/owner actions | Multi-tiered RBAC (`OWNER`, `ADMIN`, `OPERATOR`, `VIEWER`, `AUDITOR`) enforced via FastAPI dependency injection. |

## 4. Defense-in-Depth Verification
- **Network Level**: Postgres and Redis completely unexposed to public interfaces.
- **Application Level**: Strict Pydantic v2 boundary validators, OAuth2 JWT bearer tokens, SHA-256 checksums.
- **Operating System Level**: Multi-stage Docker runtime running unprivileged `appuser`.
