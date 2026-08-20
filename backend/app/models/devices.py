import enum
from datetime import datetime, timezone
from sqlalchemy import Column, Integer, Float, String, Boolean, DateTime, ForeignKey, Text, Enum
from sqlalchemy.orm import relationship
from app.core.database import Base

class DeviceStatusEnum(str, enum.Enum):
    ONLINE = "ONLINE"
    DEGRADED = "DEGRADED"
    OFFLINE = "OFFLINE"
    QUARANTINED = "QUARANTINED"

class DeviceGroup(Base):
    __tablename__ = "device_groups"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    name = Column(String(100), nullable=False)
    description = Column(String(255), nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.utcnow())

    devices = relationship("Device", back_populates="group")

class Device(Base):
    __tablename__ = "devices"

    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(String(64), unique=True, index=True, nullable=False) # Application-level UUID
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    group_id = Column(Integer, ForeignKey("device_groups.id"), nullable=True)
    
    name = Column(String(100), nullable=False)
    location_label = Column(String(100), default="Main Entrance")
    platform = Column(String(30), default="android")
    status = Column(Enum(DeviceStatusEnum), default=DeviceStatusEnum.OFFLINE, nullable=False)
    health_score = Column(Integer, default=100) # 0 to 100
    
    # Versions & Configuration
    firmware_version = Column(String(50), default="Android 11")
    app_version = Column(String(20), default="1.0.0")
    config_revision = Column(Integer, default=1)
    privacy_mode = Column(Boolean, default=False) # Disables AI/Faces & blurs video
    
    # Stream Configuration
    resolution = Column(String(20), default="720p") # 720p / 1080p
    target_fps = Column(Integer, default=30)
    target_bitrate_kbps = Column(Integer, default=1800)
    lens_facing = Column(String(10), default="BACK") # BACK / FRONT
    torch_enabled = Column(Boolean, default=False)
    
    # Recording & AI flags
    recording_mode = Column(String(20), default="MOTION") # CONTINUOUS, MOTION, MANUAL, DISABLED
    motion_sensitivity = Column(Integer, default=50)
    pre_event_seconds = Column(Integer, default=10)
    post_event_seconds = Column(Integer, default=30)
    ai_enabled = Column(Boolean, default=True)
    face_recognition_enabled = Column(Boolean, default=False)
    
    last_seen = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.utcnow())
    updated_at = Column(DateTime, default=lambda: datetime.utcnow(), onupdate=lambda: datetime.utcnow())

    group = relationship("DeviceGroup", back_populates="devices")
    status_detail = relationship("DeviceStatus", back_populates="device", uselist=False, cascade="all, delete-orphan")
    credentials = relationship("DeviceCredential", back_populates="device", cascade="all, delete-orphan")

class DeviceCredential(Base):
    __tablename__ = "device_credentials"

    id = Column(Integer, primary_key=True, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), nullable=False, index=True)
    credential_hash = Column(String(255), nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.utcnow())
    expires_at = Column(DateTime, nullable=True)
    revoked_at = Column(DateTime, nullable=True)

    device = relationship("Device", back_populates="credentials")

class DeviceStatus(Base):
    __tablename__ = "device_status"

    id = Column(Integer, primary_key=True, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), unique=True, nullable=False, index=True)
    
    battery_level = Column(Integer, default=100)
    is_charging = Column(String(20), default="AC")
    temperature_c = Column(Float, default=32.0)
    storage_free_mb = Column(Integer, default=16000)
    storage_total_mb = Column(Integer, default=64000)
    ram_free_mb = Column(Integer, default=2048)
    network_type = Column(String(20), default="WIFI")
    wifi_rssi_dbm = Column(Integer, default=-55)
    
    camera_state = Column(String(20), default="ACTIVE") # ACTIVE, ERROR, RECOVERING
    microphone_state = Column(String(20), default="ACTIVE")
    recording_state = Column(String(20), default="IDLE") # IDLE, RECORDING, BUFFERING, ERROR
    webrtc_state = Column(String(20), default="DISCONNECTED") # CONNECTED, CONNECTING, DISCONNECTED
    
    current_fps = Column(Float, default=30.0)
    current_bitrate_kbps = Column(Integer, default=1800)
    rtt_ms = Column(Integer, default=180)
    packet_loss_pct = Column(Float, default=0.0)
    
    updated_at = Column(DateTime, default=lambda: datetime.utcnow(), onupdate=lambda: datetime.utcnow())

    device = relationship("Device", back_populates="status_detail")

class PairingCode(Base):
    __tablename__ = "pairing_codes"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    code = Column(String(16), unique=True, index=True, nullable=False) # e.g. "SENT-8492"
    device_name = Column(String(100), default="New CCTV Node")
    is_used = Column(Boolean, default=False)
    expires_at = Column(DateTime, nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.utcnow())
