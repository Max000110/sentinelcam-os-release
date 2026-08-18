from datetime import datetime, timezone
from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey, Text
from app.core.database import Base

class Incident(Base):
    __tablename__ = "incidents"

    id = Column(Integer, primary_key=True, index=True)
    severity = Column(String(20), default="WARNING") # INFO, WARNING, HIGH, CRITICAL
    source = Column(String(50), default="DEVICE_MONITOR")
    fingerprint = Column(String(64), index=True, nullable=False) # For deduplication
    description = Column(String(255), nullable=False)
    affected_device_id = Column(String(64), index=True, nullable=True)
    status = Column(String(20), default="OPEN") # OPEN, ACKNOWLEDGED, RESOLVED
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), index=True)
    acknowledged_at = Column(DateTime, nullable=True)
    resolved_at = Column(DateTime, nullable=True)

class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)
    device_id = Column(String(64), nullable=True, index=True)
    action = Column(String(100), nullable=False, index=True) # e.g. LOGIN, DEVICE_ADDED, STREAM_STARTED, RESTART_COMMAND
    ip_address = Column(String(45), nullable=True)
    metadata_json = Column(Text, nullable=True)
    timestamp = Column(DateTime, default=lambda: datetime.now(timezone.utc), index=True)
