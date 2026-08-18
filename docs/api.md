# SentinelCam: API Reference

All endpoints are prefixed with `/api/v1`.

## Authentication
- `POST /api/v1/auth/register` — Create user account
- `POST /api/v1/auth/login` — Authenticate and retrieve JWT access token
- `GET /api/v1/auth/me` — Current user profile and role

## Device Management & Pairing
- `GET /api/v1/devices` — List all registered CCTV camera nodes
- `GET /api/v1/devices/{device_id}` — Get single device status
- `POST /api/v1/devices/register` — Register a new device
- `PATCH /api/v1/devices/{device_id}` — Update camera settings (torch, lens, resolution)
- `POST /api/v1/devices/pairing/generate` — Generate single-use 15-minute PIN
- `POST /api/v1/devices/pairing/claim` — Bind Android node to account with PIN

## Fleet & System Operations
- `GET /api/v1/fleet/overview` — Fleet health summary, active cameras, storage usage
- `GET /api/v1/fleet/groups` — List camera groups
- `GET /api/v1/system/health` — Global platform health (DB, Redis, Coturn, Storage)

## Recordings & Playback
- `GET /api/v1/recordings` — List recorded segments with duration and file size
- `GET /api/v1/recordings/{id}/play` — Stream MP4 with HTTP Range byte-seeking
- `PATCH /api/v1/recordings/{id}/lock` — Lock recording from retention deletion
- `POST /api/v1/recordings/upload` — Ingest finalized MP4 segment with SHA-256

## AI & Smart Zones
- `POST /api/v1/ai/events` — Ingest object detection event with bounding box
- `GET /api/v1/ai/events` — Query AI event timeline with severity filtering
- `GET /api/v1/devices/{device_id}/zones` — Get polygonal detection zones
- `POST /api/v1/devices/{device_id}/zones` — Create polygonal detection zone
- `GET /api/v1/devices/{device_id}/tripwires` — Get line-crossing tripwires

## Known People & Face Intelligence
- `GET /api/v1/faces/profiles` — List enrolled face profiles
- `POST /api/v1/faces/enroll` — Enroll authorized profile with consent & encrypted vector
- `POST /api/v1/faces/device/{device_id}/privacy-mode` — Toggle Privacy Mode

## Incidents & Diagnostics
- `GET /api/v1/incidents` — List active operational incidents
- `PATCH /api/v1/incidents/{id}` — Acknowledge or resolve incident
- `POST /api/v1/diagnostics` — Ingest sanitized Android diagnostics report
