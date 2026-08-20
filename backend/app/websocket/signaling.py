import re
import logging
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from app.services.signaling_manager import signaling_manager

logger = logging.getLogger("sentinelcam.ws")
router = APIRouter(tags=["WebRTC Signaling"])

DEVICE_ID_REGEX = re.compile(r"^[a-zA-Z0-9_-]{3,64}$")
MAX_SIGNALING_MSG_LEN = 65536  # 64 KB max for SDP / ICE candidate packets

@router.websocket("/ws/signaling/{role}/{device_id}")
async def websocket_signaling_endpoint(websocket: WebSocket, role: str, device_id: str):
    """
    WebSocket endpoint for bi-directional WebRTC signaling.
    role: 'node' (Android CCTV Phone) or 'viewer' (Web Dashboard)
    device_id: Camera identifier (validated alphanumeric/dashes/underscores)
    """
    if role not in ("node", "viewer") or not DEVICE_ID_REGEX.match(device_id):
        await websocket.close(code=1008, reason="Invalid role or device_id format")
        return

    await websocket.accept()
    
    if role == "node":
        await signaling_manager.register_node(device_id, websocket)
    elif role == "viewer":
        await signaling_manager.register_viewer(device_id, websocket)

    try:
        while True:
            message_text = await websocket.receive_text()
            if len(message_text) > MAX_SIGNALING_MSG_LEN:
                logger.warning(f"Oversized signaling message ({len(message_text)} bytes) from {role}:{device_id}")
                continue
            await signaling_manager.handle_message(websocket, message_text)
    except WebSocketDisconnect:
        await signaling_manager.disconnect(websocket)
    except Exception as e:
        logger.error(f"WebSocket signaling error ({role}:{device_id}): {e}")
        await signaling_manager.disconnect(websocket)

