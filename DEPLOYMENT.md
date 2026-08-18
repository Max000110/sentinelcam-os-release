# Deployment Guide

## Prerequisites
- A Linux VPS (Ubuntu 22.04+ recommended)
- Docker & Docker Compose installed
- A domain name (optional, but recommended for TLS)
- A public IP address

## Step 1: Clone and Configure
1. Clone the repository to your VPS.
2. Navigate to `deploy/`.
3. Create a `.env` file (ensure this is ignored by `.gitignore`!).
4. Populate it with the required secrets (replacing the `<CHANGE_ME...>` placeholders in `docker-compose.yml` if you decide to keep it as template).

## Step 2: Start the Services
```bash
cd deploy
docker-compose up -d
```

## Step 3: Network Configuration
- Ensure ports `8000` (Backend API), `3000` (Dashboard), `5432` (Postgres, if external access needed), `6379` (Redis), and `3478`, `5349`, `49152-65535` (Coturn UDP/TCP) are properly routed through your firewall.

## Step 4: Reverse Proxy
It is highly recommended to place Nginx or Caddy in front of ports `3000` and `8000` to handle SSL termination.
