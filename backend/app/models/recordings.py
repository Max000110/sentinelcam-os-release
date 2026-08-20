from datetime import datetime, timezone
from sqlalchemy import Column, Integer, Float, String, Boolean, DateTime, ForeignKey, Text
from sqlalchemy.orm import relationship
from app.core.database import Base

class Recording(Base):
    __tablename__ = "recordings"

    id = Column(Integer, primary_key=True, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), nullable=False, index=True)
    
    start_time = Column(DateTime, nullable=False, index=True)
    end_time = Column(DateTime, nullable=True)
    duration_seconds = Column(Float, default=0.0)
    file_size = Column(Integer, default=0)
    
    storage_location = Column(String(20), default="LOCAL_ANDROID") # LOCAL_ANDROID, VPS_HOT, VPS_COLD
    file_path = Column(String(255), nullable=False)
    
    codec = Column(String(20), default="H264")
    width = Column(Integer, default=1280)
    height = Column(Integer, default=720)
    fps = Column(Integer, default=30)
    audio_enabled = Column(Boolean, default=True)
    recording_mode = Column(String(30), default="MOTION_TRIGGERED") # CONTINUOUS, MOTION_TRIGGERED, MANUAL
    status = Column(String(30), default="COMPLETED") # RECORDING, COMPLETED, CORRUPTED
    
    checksum = Column(String(64), nullable=True) # SHA-256
    is_locked = Column(Boolean, default=False) # Prevents auto retention deletion
    created_at = Column(DateTime, default=lambda: datetime.utcnow())
    deleted_at = Column(DateTime, nullable=True)

    segments = relationship("RecordingSegment", back_populates="recording", cascade="all, delete-orphan")

class RecordingSegment(Base):
    __tablename__ = "recording_segments"

    id = Column(Integer, primary_key=True, index=True)
    recording_id = Column(Integer, ForeignKey("recordings.id"), nullable=False, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), nullable=False, index=True)
    
    segment_index = Column(Integer, default=0)
    start_time = Column(DateTime, nullable=False)
    end_time = Column(DateTime, nullable=False)
    duration_seconds = Column(Float, default=60.0)
    file_size = Column(Integer, default=0)
    path = Column(String(255), nullable=False)
    checksum = Column(String(64), nullable=True)
    status = Column(String(30), default="FINALIZED")
    created_at = Column(DateTime, default=lambda: datetime.utcnow())

    recording = relationship("Recording", back_populates="segments")
