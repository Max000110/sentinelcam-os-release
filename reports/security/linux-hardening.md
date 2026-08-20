# Linux Host & VPS Hardening

## 1. Host Configuration
- **OS**: Ubuntu Linux (Kernel 6.8.0)
- **Public VPS IP**: `161.118.183.23`
- **Tailscale Mesh**: Encrypted WireGuard mesh network for device-to-VPS communication.

## 2. Port & Firewall Posture
| Port | Protocol | Service | Exposure | Security Control |
| :--- | :--- | :--- | :--- | :--- |
| `22` | TCP | SSH | Public | Key-based authentication recommended. |
| `8000` | TCP | FastAPI Backend | Public | Rate limiting, CORS origin filtering, Security Headers. |
| `3000` | TCP | Next.js Dashboard | Public | Standalone Next.js runner, CSP headers. |
| `3478` / `5349` | UDP/TCP | Coturn STUN/TURN | Public | HMAC-SHA1 dynamic credentials, Denied peer IPs. |
| `5432` | TCP | PostgreSQL | **INTERNAL ONLY** | Docker bridge network; not bound to host. |
| `6379` | TCP | Redis | **INTERNAL ONLY** | Docker bridge network; not bound to host. |
