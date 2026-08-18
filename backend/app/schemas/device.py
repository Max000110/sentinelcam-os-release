from pydantic import BaseModel, ConfigDict
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

class DeviceRegister(DeviceBase):
    device_id: str

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
    api_key: str
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
