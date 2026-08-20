import re
from pydantic import BaseModel, ConfigDict, field_validator
from typing import Optional
from datetime import datetime

class DeviceBase(BaseModel):
    name: str
    resolution: Optional[str] = "1080p"
    target_fps: Optional[int] = 30
    target_bitrate_kbps: Optional[int] = 1500
    lens_facing: Optional[str] = "BACK"
    torch_enabled: Optional[bool] = False
    motion_detection_enabled: Optional[bool] = True
    motion_sensitivity: Optional[int] = 50

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 1 or len(v) > 100:
            raise ValueError("Device name must be between 1 and 100 characters")
        return v

class DeviceRegister(DeviceBase):
    device_id: str

    @field_validator("device_id")
    @classmethod
    def validate_device_id(cls, v: str) -> str:
        v = v.strip()
        if not re.match(r"^[a-zA-Z0-9_-]{3,64}$", v):
            raise ValueError("device_id must be 3-64 characters alphanumeric, underscores, or hyphens")
        return v

class DeviceUpdate(BaseModel):
    name: Optional[str] = None
    resolution: Optional[str] = None
    target_fps: Optional[int] = None
    target_bitrate_kbps: Optional[int] = None
    lens_facing: Optional[str] = None
    torch_enabled: Optional[bool] = None
    motion_detection_enabled: Optional[bool] = None
    motion_sensitivity: Optional[int] = None

class DeviceResponse(DeviceBase):
    id: int
    device_id: str
    api_key: Optional[str] = None
    is_online: bool
    last_seen_at: Optional[datetime] = None
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)

class DeviceHeartbeat(BaseModel):
    device_id: str
    battery_level: Optional[int] = None
    is_charging: Optional[str] = None
    temperature_c: Optional[float] = None
    storage_free_mb: Optional[int] = None
    storage_total_mb: Optional[int] = None
    network_type: Optional[str] = None
    wifi_rssi_dbm: Optional[int] = None
    current_fps: Optional[float] = None
    current_bitrate_kbps: Optional[int] = None
    uptime_seconds: Optional[int] = None
