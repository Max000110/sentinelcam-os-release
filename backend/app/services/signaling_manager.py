import json
import logging
from typing import Dict, Set, Optional
from fastapi import WebSocket

logger = logging.getLogger("sentinelcam.signaling")

class SignalingManager:
    def __init__(self):
        # device_id -> WebSocket (CCTV Android Node)
        self.active_nodes: Dict[str, WebSocket] = {}
        # device_id -> Set[WebSocket] (Web Viewers)
        self.active_viewers: Dict[str, Set[WebSocket]] = {}
        # reverse lookup: websocket -> (device_id, role)
        self.socket_meta: Dict[WebSocket, tuple[str, str]] = {}

    async def register_node(self, device_id: str, websocket: WebSocket):
        # Disconnect any stale connection for this node
        if device_id in self.active_nodes:
            old_ws = self.active_nodes[device_id]
            try:
                await old_ws.close(code=1000, reason="Replaced by new node connection")
            except Exception:
                pass
            if old_ws in self.socket_meta:
                del self.socket_meta[old_ws]
                
        self.active_nodes[device_id] = websocket
        self.socket_meta[websocket] = (device_id, "node")
        logger.info(f"Android CCTV Node connected: {device_id}")

        # Notify active viewers that the camera is now available
        if device_id in self.active_viewers:
            payload = json.dumps({
                "type": "node_online",
                "device_id": device_id,
                "message": "CCTV camera node is online and ready"
            })
            for viewer_ws in list(self.active_viewers[device_id]):
                try:
                    await viewer_ws.send_text(payload)
                except Exception as e:
                    logger.error(f"Error sending node_online to viewer: {e}")

    async def register_viewer(self, device_id: str, websocket: WebSocket):
        if device_id not in self.active_viewers:
            self.active_viewers[device_id] = set()
            
        self.active_viewers[device_id].add(websocket)
        self.socket_meta[websocket] = (device_id, "viewer")
        logger.info(f"Web Viewer connected to camera: {device_id}")

        # Tell viewer the initial node status
        node_online = device_id in self.active_nodes
        await websocket.send_text(json.dumps({
            "type": "room_joined",
            "device_id": device_id,
            "node_online": node_online
        }))

        # If node is online, notify the node that a viewer wants a stream
        if node_online:
            node_ws = self.active_nodes[device_id]
            try:
                await node_ws.send_text(json.dumps({
                    "type": "viewer_joined",
                    "device_id": device_id
                }))
            except Exception as e:
                logger.error(f"Failed to notify node of viewer joining: {e}")

    async def disconnect(self, websocket: WebSocket):
        if websocket not in self.socket_meta:
            return
            
        device_id, role = self.socket_meta.pop(websocket)
        
        if role == "node":
            if device_id in self.active_nodes and self.active_nodes[device_id] == websocket:
                del self.active_nodes[device_id]
                logger.info(f"Android CCTV Node disconnected: {device_id}")
                
                # Notify viewers that node went offline
                if device_id in self.active_viewers:
                    payload = json.dumps({
                        "type": "node_offline",
                        "device_id": device_id
                    })
                    for viewer_ws in list(self.active_viewers[device_id]):
                        try:
                            await viewer_ws.send_text(payload)
                        except Exception:
                            pass
        elif role == "viewer":
            if device_id in self.active_viewers:
                self.active_viewers[device_id].discard(websocket)
                if not self.active_viewers[device_id]:
                    del self.active_viewers[device_id]
                logger.info(f"Web Viewer disconnected from camera: {device_id}")
                
                # Notify node if no viewers left
                if device_id in self.active_nodes:
                    node_ws = self.active_nodes[device_id]
                    viewers_remaining = len(self.active_viewers.get(device_id, set()))
                    try:
                        await node_ws.send_text(json.dumps({
                            "type": "viewer_left",
                            "device_id": device_id,
                            "viewers_count": viewers_remaining
                        }))
                    except Exception:
                        pass

    async def handle_message(self, websocket: WebSocket, raw_text: str):
        try:
            data = json.loads(raw_text)
        except Exception:
            logger.warning(f"Invalid JSON received on signaling: {raw_text[:100]}")
            return

        msg_type = data.get("type")
        device_id = data.get("device_id")
        
        if not device_id or websocket not in self.socket_meta:
            return
            
        _, sender_role = self.socket_meta[websocket]

        if sender_role == "node":
            # Message from Android node -> broadcast/forward to viewers in room
            if device_id in self.active_viewers:
                for viewer_ws in list(self.active_viewers[device_id]):
                    try:
                        await viewer_ws.send_text(raw_text)
                    except Exception as e:
                        logger.error(f"Error forwarding node message to viewer: {e}")
        elif sender_role == "viewer":
            # Message from Viewer -> forward to Android node
            if device_id in self.active_nodes:
                node_ws = self.active_nodes[device_id]
                try:
                    await node_ws.send_text(raw_text)
                except Exception as e:
                    logger.error(f"Error forwarding viewer message to node: {e}")

    def is_node_online(self, device_id: str) -> bool:
        return device_id in self.active_nodes

signaling_manager = SignalingManager()
