# Redis Presence & PubSub Security

## 1. Network Exposure
- **Isolated Internal Network**: Redis 7 Alpine is strictly internal to Docker compose; no host port mapping (`6379` unexposed to host/WAN).
- **Command Disabling**: Administrative commands like `FLUSHALL`, `CONFIG`, `DEBUG` restricted.
- **Resource Constraints**: Capped at 256MB RAM and 0.5 CPU cores to prevent memory ballooning.
