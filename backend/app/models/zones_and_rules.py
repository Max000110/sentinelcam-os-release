import enum
from datetime import datetime, timezone
from sqlalchemy import Column, Integer, Float, String, Boolean, DateTime, ForeignKey, Text, Enum
from app.core.database import Base

class ZoneType(str, enum.Enum):
    PROTECTED = "PROTECTED"
    IGNORED = "IGNORED"
    MONITOR = "MONITOR"

class TripwireDirection(str, enum.Enum):
    A_TO_B = "A_TO_B"
    B_TO_A = "B_TO_A"
    ANY = "ANY"

class Zone(Base):
    __tablename__ = "zones"

    id = Column(Integer, primary_key=True, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), nullable=False, index=True)
    name = Column(String(100), nullable=False)
    zone_type = Column(Enum(ZoneType), default=ZoneType.PROTECTED, nullable=False)
    polygon_json = Column(Text, nullable=False) # e.g. "[[0.1, 0.2], [0.4, 0.2], [0.4, 0.6], [0.1, 0.6]]"
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=lambda: datetime.utcnow())

class Tripwire(Base):
    __tablename__ = "tripwires"

    id = Column(Integer, primary_key=True, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), nullable=False, index=True)
    name = Column(String(100), nullable=False)
    point_a_x = Column(Float, nullable=False)
    point_a_y = Column(Float, nullable=False)
    point_b_x = Column(Float, nullable=False)
    point_b_y = Column(Float, nullable=False)
    direction = Column(Enum(TripwireDirection), default=TripwireDirection.ANY, nullable=False)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=lambda: datetime.utcnow())

class Rule(Base):
    __tablename__ = "rules"

    id = Column(Integer, primary_key=True, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), nullable=False, index=True)
    name = Column(String(100), nullable=False)
    enabled = Column(Boolean, default=True)
    conditions_json = Column(Text, nullable=False) # JSON condition object
    actions_json = Column(Text, nullable=False) # JSON action object (notify, record, severity)
    created_at = Column(DateTime, default=lambda: datetime.utcnow())
