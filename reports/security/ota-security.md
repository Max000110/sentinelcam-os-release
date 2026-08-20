# OTA Firmware & App Update Security

## 1. Integrity & Authenticity Verification
- **SHA-256 Checksums**: Mandatory 64-character hex checksum validation on every OTA package.
- **Rollback Protection**: Releases can be marked as `REVOKED` via `/api/v1/ota/rollback`, instantly invalidating malicious or unstable versions.
- **RBAC Publishing**: Publishing and revoking OTA releases strictly restricted to `OWNER` and `ADMIN` roles.
- **Package Identity**: Android OS enforces APK signature matching against original developer keystore on updates.
