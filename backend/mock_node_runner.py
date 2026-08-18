"""
SentinelCam Mock Node Runner
Simulates an Android Phone CCTV node connecting over WebSocket signaling and sending live telemetry heartbeats.
"""
import asyncio
import json
import random
import time
import httpx
import websockets

BACKEND_HTTP = "http://127.0.0.1:8000"
BACKEND_WS = "ws://127.0.0.1:8000"
DEVICE_ID = "cam_livingroom_01"

async def send_heartbeat_loop():
    print(f"[*] Starting simulated telemetry loop for {DEVICE_ID}...")
    uptime = 0
    while True:
        try:
            telemetry_data = {
                "device_id": DEVICE_ID,
                "battery_level": random.randint(80, 95),
                "is_charging": "AC",
                "temperature_c": round(32.5 + random.uniform(-0.5, 1.2), 1),
                "storage_free_mb": 18450,
                "storage_total_mb": 64000,
                "network_type": "WIFI",
                "wifi_rssi_dbm": random.randint(-58, -48),
                "current_fps": 30.0,
                "current_bitrate_kbps": 1850,
                "uptime_seconds": uptime
            }
            async with httpx.AsyncClient(timeout=5.0) as client:
                res = await client.post(f"{BACKEND_HTTP}/api/v1/telemetry/heartbeat", json=telemetry_data)
                if res.status_code == 200:
                    print(f"[Telemetry] Sent heartbeat (Battery: {telemetry_data['battery_level']}%, Temp: {telemetry_data['temperature_c']}°C, RSSI: {telemetry_data['wifi_rssi_dbm']}dBm)")
        except Exception as e:
            print(f"[Telemetry Error] {e}")
            
        uptime += 10
        await asyncio.sleep(10)

async def signaling_loop():
    uri = f"{BACKEND_WS}/ws/signaling/node/{DEVICE_ID}"
    while True:
        try:
            print(f"[*] Connecting node to signaling server: {uri}")
            async with websockets.connect(uri) as ws:
                print(f"[+] Connected to signaling server as node: {DEVICE_ID}")
                async for message in ws:
                    data = json.loads(message)
                    msg_type = data.get("type")
                    print(f"[Signaling Received] Type: {msg_type}")
                    
                    if msg_type == "viewer_joined":
                        print("[Signaling] Viewer joined! Sending simulated WebRTC Offer...")
                        offer_msg = {
                            "type": "offer",
                            "device_id": DEVICE_ID,
                            "sender_role": "node",
                            "sdp": "v=0\r\no=SentinelCamNode 123456 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=sendrecv\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\na=rtpmap:96 H264/90000"
                        }
                        await ws.send(json.dumps(offer_msg))
                    elif msg_type == "answer":
                        print("[Signaling] WebRTC Answer received from viewer! Stream handshake active.")
                    elif msg_type == "command":
                        cmd = data.get("command")
                        print(f"[Command Received] Executing node command: {cmd}")
        except Exception as e:
            print(f"[Signaling Error] {e}. Reconnecting in 3s...")
            await asyncio.sleep(3)

async def main():
    await asyncio.gather(
        signaling_loop(),
        send_heartbeat_loop()
    )

if __name__ == "__main__":
    asyncio.run(main())
