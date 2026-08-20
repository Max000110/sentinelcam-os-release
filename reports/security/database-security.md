# PostgreSQL Database Security & Hardening

## 1. Network & Access Isolation
- **No External Port Exposure**: PostgreSQL 16 Alpine container is strictly bound to Docker internal bridge network.
- **Role & Privilege Segregation**: Unprivileged database user `sentinel` owns the application schema.
- **Parameterized SQL**: 100% of queries execute via SQLAlchemy async ORM with parameterized prepared statements, preventing SQL injection.

## 2. Connection Pooling & Resource Management
- **AsyncAdaptedQueuePool**: Configured with `pool_size=10`, `max_overflow=20`, `pool_pre_ping=True`, `pool_recycle=1800`.
- **Query Indexing**: High-performance B-tree indexes applied on `Device.device_id`, `Device.user_id`, `AiEvent.severity`, `Recording.start_time`, `UserSession.token_jti`.
