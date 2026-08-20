from datetime import datetime, timezone
from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey, Text
from app.core.database import Base

class OtaRelease(Base):
    __tablename__ = "ota_releases"

    id = Column(Integer, primary_key=True, index=True)
    version = Column(String(30), unique=True, index=True, nullable=False) # e.g. "1.2.0"
    release_id = Column(String(64), unique=True, nullable=False)
    sha256 = Column(String(64), nullable=False)
    package_url = Column(String(255), nullable=False)
    min_android_version = Column(Integer, default=24) # Android 7.0+
    release_channel = Column(String(20), default="STABLE") # STABLE, BETA, CANARY
    release_notes = Column(Text, nullable=True)
    status = Column(String(20), default="ACTIVE") # ACTIVE, DEPRECATED, REVOKED
    created_at = Column(DateTime, default=lambda: datetime.utcnow())

class DiagnosticReport(Base):
    __tablename__ = "diagnostic_reports"

    id = Column(Integer, primary_key=True, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), nullable=False, index=True)
    diagnostic_json = Column(Text, nullable=False) # Sanitized metrics, memory, battery, WebRTC errors
    expires_at = Column(DateTime, nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.utcnow())
