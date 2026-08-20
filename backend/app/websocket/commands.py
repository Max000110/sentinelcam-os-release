import re
import json
import logging
from typing import Dict
from fastapi import APIRouter, WebSocket, WebSocketDisconnect

logger = logging.getLogger("sentinelcam.ws_commands")
router = APIRouter(tags=["Remote Command Hub"])

DEVICE_ID_REGEX = re.compile(r"^[a-zA-Z0-9_-]{3,64}$")

ALLOWLISTED_COMMANDS = {
    "RESTART_SERVICE",
    "RESTART_STREAM",
    "SWITCH_CAMERA",
    "TOGGLE_TORCH",
    "SET_PRIVACY_MODE",
    "SYNC_CONFIG",
    "REQUEST_DIAGNOSTICS",
    "START_RECORDING",
    "STOP_RECORDING"
}

class CommandHub:
    def __init__(self):
        # device_id -> WebSocket (Node connection)
        self.node_connections: Dict[str, WebSocket] = {}

    async def register_node(self, device_id: str, websocket: WebSocket):
        await websocket.accept()
        self.node_connections[device_id] = websocket
        logger.info(f"Command hub registered node: {device_id}")

    def unregister_node(self, device_id: str):
        if device_id in self.node_connections:
            del self.node_connections[device_id]

    async def dispatch_command(self, device_id: str, command: str, command_id: str, payload: dict = None) -> bool:
        if command not in ALLOWLISTED_COMMANDS:
            logger.warning(f"Rejected non-allowlisted command: {command}")
            return False
            
        if device_id not in self.node_connections:
            logger.warning(f"Cannot dispatch command: Node {device_id} offline")
            return False

        ws = self.node_connections[device_id]
        msg = {
            "type": "command",
            "command_id": command_id,
            "command": command,
            "payload": payload or {}
        }
        await ws.send_text(json.dumps(msg))
        return True

command_hub = CommandHub()

@router.websocket("/ws/commands/{device_id}")
async def command_hub_endpoint(websocket: WebSocket, device_id: str):
    if not DEVICE_ID_REGEX.match(device_id):
        await websocket.close(code=1008, reason="Invalid device_id format")
        return

    await command_hub.register_node(device_id, websocket)
    try:
        while True:
            text = await websocket.receive_text()
            if len(text) > 16384:
                continue
            data = json.loads(text)
            if data.get("type") == "command_ack":
                logger.info(f"Node {device_id} ACK command: {data.get('command_id')}")
    except WebSocketDisconnect:
        command_hub.unregister_node(device_id)
    except Exception as e:
        logger.error(f"Command WebSocket error for {device_id}: {e}")
        command_hub.unregister_node(device_id)

