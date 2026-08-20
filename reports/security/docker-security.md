# Docker Container Hardening

## 1. Container Best Practices
- **Multi-Stage Builds**: Minimal runtime footprint with separate compilation and runtime stages.
- **Non-Root Execution**: Backend container runs as unprivileged user `appuser` (`UID 1000`).
- **Resource Boundaries**: Strict CPU and memory limits on all compose services.
- **Health Checks**: Automated container liveness and readiness probes on Postgres, Redis, and Backend.
- **.dockerignore**: Excludes `.git`, `tests`, `venv`, secrets, and caches from image build context.
