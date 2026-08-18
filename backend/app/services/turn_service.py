import time
import hmac
import hashlib
import base64
from typing import List
from app.core.config import settings
from app.schemas.signaling import IceServerConfig, RtcConfigurationResponse

class TurnService:
    """
    Generates standard ephemeral Coturn STUN/TURN credentials using HMAC-SHA1.
    Compatible with turnserver dynamic auth secret configuration.
    """
    @staticmethod
    def generate_ice_servers(user_id: str = "sentinel_viewer") -> RtcConfigurationResponse:
        ice_servers: List[IceServerConfig] = []
        
        # Public Google STUN server for quick NAT mapping
        ice_servers.append(
            IceServerConfig(
                urls=[
                    "stun:stun.l.google.com:19302",
                    "stun:stun1.l.google.com:19302"
                ]
            )
        )
        
        if settings.COTURN_ENABLED:
            # Ephemeral credential expiration timestamp
            expiry_time = int(time.time()) + settings.TURN_CREDENTIAL_TTL_SECONDS
            username = f"{expiry_time}:{user_id}"
            
            # HMAC-SHA1 key generation
            key = settings.COTURN_STATIC_AUTH_SECRET.encode("utf-8")
            msg = username.encode("utf-8")
            hashed = hmac.new(key, msg, hashlib.sha1).digest()
            credential = base64.b64encode(hashed).decode("utf-8")
            
            # STUN/TURN UDP and TCP
            turn_urls = [
                f"stun:{settings.COTURN_PUBLIC_IP}:{settings.COTURN_PORT}",
                f"turn:{settings.COTURN_PUBLIC_IP}:{settings.COTURN_PORT}?transport=udp",
                f"turn:{settings.COTURN_PUBLIC_IP}:{settings.COTURN_PORT}?transport=tcp",
                f"turns:{settings.COTURN_PUBLIC_IP}:{settings.COTURN_TURNS_PORT}?transport=tcp"
            ]
            
            ice_servers.append(
                IceServerConfig(
                    urls=turn_urls,
                    username=username,
                    credential=credential
                )
            )
            
        return RtcConfigurationResponse(iceServers=ice_servers)

turn_service = TurnService()
