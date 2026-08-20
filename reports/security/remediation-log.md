# Security Remediation & Patch Log

## Commit Log & Code Patches
1. **Commit `39ee7b7`**: Performance and memory leak fixes across CameraEngine buffer reuse, standalone Next.js packaging, and PostgreSQL connection pooling.
2. **Commit `c70880c`**: Resolved Python 3.12 datetime deprecations, added database indexes, added non-blocking bcrypt hashing, and hardened Docker compose with healthchecks.
3. **Commit `b6f3102`**: Fixed WebRTC telemetry stats interval memory leak, cleaned up Android executor threads on service destroy, and restricted SDP bitrate munging to H.264 video.
4. **Commit `6c414f5`**: Enforced deactivated user token rejection, hardened RFC 9110 HTTP Range streaming (416 responses), upgraded pairing code entropy, and enabled browser Permissions-Policy camera/mic access.
