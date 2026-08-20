import enum
from datetime import datetime, timezone
from sqlalchemy import Column, Integer, Float, String, DateTime, ForeignKey, Text, Enum
from app.core.database import Base

class EventSeverity(str, enum.Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"

class MotionEvent(Base):
    __tablename__ = "motion_events"

    id = Column(Integer, primary_key=True, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), nullable=False, index=True)
    
    started_at = Column(DateTime, default=lambda: datetime.utcnow(), index=True)
    ended_at = Column(DateTime, nullable=True)
    confidence = Column(Float, default=1.0)
    event_type = Column(String(50), default="MOTION")
    recording_id = Column(Integer, ForeignKey("recordings.id"), nullable=True)
    thumbnail_path = Column(String(255), nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.utcnow())

class AiEvent(Base):
    __tablename__ = "ai_events"

    id = Column(Integer, primary_key=True, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), nullable=False, index=True)
    
    event_type = Column(String(60), default="PERSON_DETECTED", index=True)
    severity = Column(Enum(EventSeverity), default=EventSeverity.MEDIUM, nullable=False)
    object_class = Column(String(40), default="person") # person, car, motorcycle, bicycle, etc.
    track_id = Column(Integer, nullable=True)
    
    start_time = Column(DateTime, default=lambda: datetime.utcnow(), index=True)
    end_time = Column(DateTime, nullable=True)
    duration_seconds = Column(Float, default=0.0)
    
    zone_id = Column(Integer, nullable=True)
    recording_id = Column(Integer, ForeignKey("recordings.id"), nullable=True)
    thumbnail_path = Column(String(255), nullable=True)
    
    max_confidence = Column(Float, default=0.90)
    avg_confidence = Column(Float, default=0.85)
    bbox_json = Column(Text, nullable=True) # e.g. {"x": 0.3, "y": 0.2, "w": 0.2, "h": 0.5}
    
    model_name = Column(String(50), default="yolov8n-tflite")
    model_version = Column(String(20), default="1.0.0")
    status = Column(String(30), default="CONFIRMED")
    
    created_at = Column(DateTime, default=lambda: datetime.utcnow())
