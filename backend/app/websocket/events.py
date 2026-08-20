import re
import json
import logging
from typing import Dict, Set
from fastapi import APIRouter, WebSocket, WebSocketDisconnect

logger = logging.getLogger("sentinelcam.ws_events")
router = APIRouter(tags=["Real-time Event Stream"])

DEVICE_ID_REGEX = re.compile(r"^[a-zA-Z0-9_-]{3,64}$")

class EventStreamManager:
    def __init__(self):
        # device_id -> Set[WebSocket]
        self.listeners: Dict[str, Set[WebSocket]] = {}

    async def connect(self, device_id: str, websocket: WebSocket):
        await websocket.accept()
        if device_id not in self.listeners:
            self.listeners[device_id] = set()
        self.listeners[device_id].add(websocket)

    def disconnect(self, device_id: str, websocket: WebSocket):
        if device_id in self.listeners:
            self.listeners[device_id].discard(websocket)
            if not self.listeners[device_id]:
                del self.listeners[device_id]

    async def broadcast(self, device_id: str, message: dict):
        if device_id in self.listeners:
            payload = json.dumps(message)
            for ws in list(self.listeners[device_id]):
                try:
                    await ws.send_text(payload)
                except Exception:
                    pass

event_stream_manager = EventStreamManager()

@router.websocket("/ws/events/{device_id}")
async def event_stream_endpoint(websocket: WebSocket, device_id: str):
    if not DEVICE_ID_REGEX.match(device_id):
        await websocket.close(code=1008, reason="Invalid device_id format")
        return

    await event_stream_manager.connect(device_id, websocket)
    try:
        while True:
            # Receive any incoming AI detections or telemetry from node
            data_text = await websocket.receive_text()
            if len(data_text) > 32768:
                continue
            data = json.loads(data_text)
            await event_stream_manager.broadcast(device_id, data)
    except WebSocketDisconnect:
        event_stream_manager.disconnect(device_id, websocket)
    except Exception as e:
        logger.error(f"Event WebSocket error: {e}")
        event_stream_manager.disconnect(device_id, websocket)

