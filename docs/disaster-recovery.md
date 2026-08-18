# SentinelCam: Disaster Recovery & Operations Runbook

## 1. Disaster Recovery Objectives
- **Recovery Point Objective (RPO):** $\le 24$ hours for PostgreSQL metadata.
- **Recovery Time Objective (RTO):** $\le 4$ hours for complete VPS rebuild.

---

## 2. Backup & Restore Procedures

### Database Backup
```bash
# Automated daily PostgreSQL backup
pg_dump -U sentinel -d sentinelcam -F c -b -v -f /backups/sentinelcam_$(date +%Y%m%d).dump
```

### Database Restore
```bash
# Clean database restore
pg_restore -U sentinel -d sentinelcam -v -c /backups/sentinelcam_20260818.dump
```

---

## 3. Operational Runbook

### Service Health Verification
```bash
curl -f http://localhost:8000/api/v1/system/health
```

### Coturn Verification
```bash
# Test STUN resolution
turnutils_stunclient 127.0.0.1 3478
```

### Clean Expired Retention Storage
```bash
# Run manual retention sweep via backend
curl -X POST http://localhost:8000/api/v1/system/cleanup
```
