# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability within SentinelCam, please report it responsibly.

**DO NOT** open a public GitHub issue for security vulnerabilities.

Instead, please email: **security@example.com** (replace with your actual security contact)

You should receive an acknowledgment within 48 hours. We will work with you to understand and address the issue before any public disclosure.

## Security Best Practices for Deployment

### Secrets Management

- **Never** commit `.env` files, keystores, private keys, or credentials to version control.
- Use the provided `.env.example` as a template and create your own `.env` file locally.
- Rotate all secrets (JWT, TURN, database passwords) on a regular schedule.
- Use strong, randomly generated secrets (minimum 32 characters).

### TURN Server

- Always set a unique `COTURN_STATIC_AUTH_SECRET` — never use default values.
- Restrict relay IP ranges to prevent SSRF attacks (already configured in `turnserver.conf`).
- Use TLS for TURN connections in production (`tls-listening-port`).

### Database

- Use PostgreSQL in production (not SQLite).
- Set a strong, unique database password.
- Restrict database network access to backend services only.

### Android Signing

- Never commit release keystores (`.jks`, `.keystore`) to version control.
- Store signing credentials securely (e.g., CI/CD secrets, secure vault).
- Rotate signing keys if they are ever exposed.

### Network

- Use HTTPS/WSS in production for all API and WebSocket connections.
- Configure proper firewall rules for TURN UDP port range (49152–65535).
- Do not expose PostgreSQL (5432) or Redis (6379) ports publicly.

### Authentication

- Change the default admin password immediately after first deployment.
- Use strong passwords for all user accounts.
- JWT tokens expire after 24 hours by default.

## Disclosure Policy

We follow responsible disclosure practices. After a fix is released, we will publicly acknowledge the vulnerability and the reporter (unless anonymity is requested).
