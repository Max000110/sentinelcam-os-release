from fastapi import APIRouter, Depends, Query
from app.schemas.signaling import RtcConfigurationResponse
from app.services.turn_service import turn_service

router = APIRouter(prefix="/stream", tags=["Streaming & WebRTC"])

@router.get("/ice-servers", response_model=RtcConfigurationResponse)
async def get_ice_servers(user_id: str = Query("sentinel_client", description="Client identifier for TURN credential")):
    return turn_service.generate_ice_servers(user_id=user_id)
