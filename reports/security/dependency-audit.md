# Dependency Security & Supply-Chain Audit

## 1. Python Backend Dependencies
- `fastapi` (0.115.8) - Clean, no known CVEs.
- `sqlalchemy` (2.0.38) - Clean, asyncpg compatible.
- `bcrypt` (4.2.1) - Secure native C-binding for password hashing.
- `python-jose` (3.3.0) / `cryptography` (44.0.1) - Robust cryptographic primitives.

## 2. Dashboard Dependencies
- `next` (14.2.35) - Standalone optimized output.
- `lucide-react` (0.475.0) - Tree-shaken icons.
- ESLint Audit: 0 warnings, 0 errors.

## 3. Android Dependencies
- `androidx.camera:camera-*` (1.4.1) - Official Android CameraX.
- `io.getstream:stream-webrtc-android` (1.3.0) - Production WebRTC build with hardware codecs.
- `androidx.security:security-crypto` (1.1.0-alpha06) - Keystore-backed AES-256.
