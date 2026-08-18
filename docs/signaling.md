# SentinelCam: WebRTC Signaling & NAT Traversal

## WebSocket Signaling Protocol
Endpoint: `/ws/signaling/{role}/{device_id}`
- `role`: `node` (Android CCTV phone) or `viewer` (Web dashboard)
- `device_id`: Target camera identifier

### Message Exchange Flow
1. **Viewer Joins:** Sends connection request. Backend notifies Node:
   ```json
   {"type": "viewer_joined", "device_id": "cam01"}
   ```
2. **Node Generates Offer:** Android node creates SDP Offer:
   ```json
   {
     "type": "offer",
     "device_id": "cam01",
     "sender_role": "node",
     "sdp": "v=0\r\no=SentinelCam ..."
   }
   ```
3. **Viewer Generates Answer:** Browser receives Offer, sets remote description, responds with Answer:
   ```json
   {
     "type": "answer",
     "device_id": "cam01",
     "sender_role": "viewer",
     "sdp": "v=0\r\no=WebClient ..."
   }
   ```
4. **ICE Candidate Exchange:** Both peers exchange candidate packets:
   ```json
   {
     "type": "ice_candidate",
     "device_id": "cam01",
     "sender_role": "node",
     "candidate": {
       "sdpMid": "0",
       "sdpMLineIndex": 0,
       "candidate": "candidate:842163049 1 udp 1677729535 ..."
     }
   }
   ```

---

## Coturn Dynamic STUN/TURN Integration
- Time-limited ephemeral HMAC-SHA1 tokens issued via `/api/v1/stream/ice-servers`.
- Generates `stun:ip:3478`, `turn:ip:3478?transport=udp`, `turn:ip:3478?transport=tcp`, and `turns:ip:5349?transport=tcp`.
- Allows traversal through symmetric NATs, firewalls, and 4G/5G mobile carriers.
