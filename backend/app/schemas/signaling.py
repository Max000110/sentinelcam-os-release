from pydantic import BaseModel, ConfigDict
from typing import Optional, List, Dict, Any
from datetime import datetime

class IceServerConfig(BaseModel):
    urls: List[str]
    username: Optional[str] = None
    credential: Optional[str] = None

class RtcConfigurationResponse(BaseModel):
    iceServers: List[IceServerConfig]
    iceTransportPolicy: Optional[str] = "all"

class SignalingMessage(BaseModel):
    type: str # 'offer', 'answer', 'ice_candidate', 'join', 'leave', 'ping', 'pong', 'command'
    device_id: str
    sender_role: str # 'node' or 'viewer'
    sdp: Optional[str] = None
    candidate: Optional[Dict[str, Any]] = None
    command: Optional[str] = None
    payload: Optional[Dict[str, Any]] = None

class MotionEventCreate(BaseModel):
    device_id: str
    event_type: Optional[str] = "MOTION_DETECTED"
    confidence: Optional[float] = 1.0
    timestamp: Optional[datetime] = None
    metadata: Optional[Dict[str, Any]] = None

class MotionEventResponse(BaseModel):
    id: int
    device_id: str
    event_type: str
    confidence: float
    snapshot_url: Optional[str] = None
    video_clip_url: Optional[str] = None
    timestamp: datetime

    model_config = ConfigDict(from_attributes=True)
