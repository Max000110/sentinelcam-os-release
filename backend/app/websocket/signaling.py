import logging
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from app.services.signaling_manager import signaling_manager

logger = logging.getLogger("sentinelcam.ws")
router = APIRouter(tags=["WebRTC Signaling"])

@router.websocket("/ws/signaling/{role}/{device_id}")
async def websocket_signaling_endpoint(websocket: WebSocket, role: str, device_id: str):
    """
    WebSocket endpoint for bi-directional WebRTC signaling.
    role: 'node' (Android CCTV Phone) or 'viewer' (Web Dashboard)
    device_id: Camera identifier
    """
    await websocket.accept()
    
    if role == "node":
        await signaling_manager.register_node(device_id, websocket)
    elif role == "viewer":
        await signaling_manager.register_viewer(device_id, websocket)
    else:
        await websocket.close(code=1008, reason="Invalid role. Must be 'node' or 'viewer'")
        return

    try:
        while True:
            message_text = await websocket.receive_text()
            await signaling_manager.handle_message(websocket, message_text)
    except WebSocketDisconnect:
        await signaling_manager.disconnect(websocket)
    except Exception as e:
        logger.error(f"WebSocket signaling error ({role}:{device_id}): {e}")
        await signaling_manager.disconnect(websocket)
