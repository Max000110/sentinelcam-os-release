# REST API Security & RBAC Audit

## 1. Authentication & Session Management
- **OAuth2 Bearer Tokens**: Cryptographically signed HS256 JWT tokens with configurable expiration (`ACCESS_TOKEN_EXPIRE_MINUTES = 1440`).
- **Account State Verification**: `get_current_user` and `login` explicitly verify `user.is_active`, immediately rejecting deactivated or suspended accounts with HTTP 403.
- **Bcrypt Hash Delegation**: Password verification and hashing offloaded to threadpool via `async_verify_password` and `async_get_password_hash`, protecting asyncio workers against CPU exhaustion.

## 2. Input Boundary Validation & Parameter Sanitization
- **Strict Pydantic v2 Schemas**: All incoming request bodies validated for types, bounds, string length, and format.
- **Device ID Regex**: Constrained to `^[a-zA-Z0-9_-]{3,64}$` to eliminate SQL, path traversal, or command injection.
- **Normalized Coordinate Bounds**: Detection zones and tripwires validated against `0.0 <= x,y <= 1.0`.

## 3. IDOR & Access Controls
- **Role-Based Access Control**: Standardized across `OWNER`, `ADMIN`, `OPERATOR`, `VIEWER`, `AUDITOR`.
- **Device Pairing Security**: Single-use high-entropy pairing codes (`SENT-XXXXXX`, 16.7M combinations) with 15-minute expiration.
