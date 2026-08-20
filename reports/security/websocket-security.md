# WebSocket Signaling & Real-Time Security

## 1. Endpoint Inventory
- `/ws/signaling/{role}/{device_id}`: Bi-directional WebRTC SDP and ICE candidate exchange.
- `/ws/events/{device_id}`: Real-time AI detection events and motion timeline broadcaster.
- `/ws/commands/{device_id}`: Remote hardware command hub.

## 2. Handshake & Channel Isolation
- **Role & Device Validation**: Handshake validates `role in ('node', 'viewer')` and matches `device_id` regex.
- **Signaling Channel Segregation**: Viewer and node sockets mapped in dedicated per-device room sets (`SignalingManager.active_nodes`, `SignalingManager.active_viewers`), preventing cross-device signaling eavesdropping.
- **Frame Length Capping**: Enforces maximum frame length (64 KB for signaling, 32 KB for events, 16 KB for commands).
- **Malformed JSON Resiliency**: Guards payload deserialization with try-except blocks to prevent unexpected socket closure on invalid frames.
