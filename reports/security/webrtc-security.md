# WebRTC Security & DTLS-SRTP Implementation

## 1. Media Encryption
- **DTLS-SRTP**: WebRTC standard mandatory end-to-end encryption for video (H.264) and audio (Opus).
- **Candidate Leakage Prevention**: WebRTC ICE gathering uses unified plan SDP with dynamic turn credentials.

## 2. Low-Latency SDP Munging Security
- **Targeted Payload Tuning**: Bitrate limits (`x-google-start-bitrate=1200;x-google-max-bitrate=2500`) applied strictly to the negotiated H.264 video payload (`a=fmtp:$h264Pt`), avoiding corruption of audio transceivers.
- **Decoupled Stream IDs**: Video (`ARDAMS_V`) and audio (`ARDAMS_A`) tracks separated to prevent browser NetEq synchronization stalls while preserving media integrity.
