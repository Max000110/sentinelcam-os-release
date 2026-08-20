import { useEffect, useRef, useState, useCallback } from "react";
import { fetchIceServers } from "../lib/api";

export interface StreamState {
  isConnected: boolean;
  isStreaming: boolean;
  nodeOnline: boolean;
  latencyMs: number;
  isMicActive: boolean;
  error: string | null;
}

export function useWebRtcStream(deviceId: string) {
  const [state, setState] = useState<StreamState>({
    isConnected: false,
    isStreaming: false,
    nodeOnline: false,
    latencyMs: 180, // estimated initial glass-to-glass
    isMicActive: false,
    error: null,
  });

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const pcRef = useRef<RTCPeerConnection | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const localMicStreamRef = useRef<MediaStream | null>(null);

  const WS_BASE = typeof window !== 'undefined' 
    ? `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.hostname}:8000/ws/signaling/viewer/${deviceId}`
    : `ws://127.0.0.1:8000/ws/signaling/viewer/${deviceId}`;

  const sendSignalingMessage = useCallback((msg: object) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(msg));
    }
  }, []);

  const sendCommand = useCallback((command: string, payload?: object) => {
    sendSignalingMessage({
      type: "command",
      device_id: deviceId,
      sender_role: "viewer",
      command,
      payload,
    });
  }, [deviceId, sendSignalingMessage]);

  const toggleMic = useCallback(async () => {
    if (state.isMicActive) {
      // Disable mic
      if (localMicStreamRef.current) {
        localMicStreamRef.current.getTracks().forEach(t => t.stop());
        localMicStreamRef.current = null;
      }
      setState(prev => ({ ...prev, isMicActive: false }));
    } else {
      // Enable mic and add track to WebRTC PeerConnection
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        localMicStreamRef.current = stream;
        if (pcRef.current) {
          stream.getAudioTracks().forEach(track => {
            pcRef.current?.addTrack(track, stream);
          });
        }
        setState(prev => ({ ...prev, isMicActive: true }));
      } catch (err: any) {
        console.error("Failed to access microphone:", err);
        alert("Microphone permission required for Two-Way Intercom");
      }
    }
  }, [state.isMicActive]);

  useEffect(() => {
    let isSubscribed = true;

    async function initWebRtc() {
      try {
        const rtcConfig = await fetchIceServers();
        if (!isSubscribed) return;

        const pc = new RTCPeerConnection({
          iceServers: rtcConfig.iceServers,
          bundlePolicy: "max-bundle",
          rtcpMuxPolicy: "require",
        });
        pcRef.current = pc;

        // Handle incoming remote media tracks (Video & Audio from Android Phone)
        pc.ontrack = (event) => {
          if (videoRef.current && event.streams[0]) {
            videoRef.current.srcObject = event.streams[0];
            setState(prev => ({ ...prev, isStreaming: true }));
          }
        };

        // ICE candidate generation
        pc.onicecandidate = (event) => {
          if (event.candidate) {
            sendSignalingMessage({
              type: "ice_candidate",
              device_id: deviceId,
              sender_role: "viewer",
              candidate: event.candidate,
            });
          }
        };

        // Connect WebSocket Signaling
        const ws = new WebSocket(WS_BASE);
        wsRef.current = ws;

        ws.onopen = () => {
          if (!isSubscribed) return;
          setState(prev => ({ ...prev, isConnected: true, error: null }));
        };

        ws.onmessage = async (event) => {
          try {
            const data = JSON.parse(event.data);
            if (data.type === "room_joined") {
              setState(prev => ({ ...prev, nodeOnline: !!data.node_online }));
            } else if (data.type === "node_online" || (data.type === "node_status" && data.is_online)) {
              setState(prev => ({ ...prev, nodeOnline: true }));
            } else if (data.type === "node_offline" || (data.type === "node_status" && !data.is_online)) {
              setState(prev => ({ ...prev, nodeOnline: false, isStreaming: false }));
            } else if (data.type === "offer") {
              // Received Offer from Android Phone
              await pc.setRemoteDescription(new RTCSessionDescription({ type: "offer", sdp: data.sdp }));
              const answer = await pc.createAnswer();
              await pc.setLocalDescription(answer);

              sendSignalingMessage({
                type: "answer",
                device_id: deviceId,
                sender_role: "viewer",
                sdp: answer.sdp,
              });
            } else if (data.type === "ice_candidate" && data.candidate) {
              await pc.addIceCandidate(new RTCIceCandidate(data.candidate));
            }
          } catch (e) {
            console.error("Signaling message handling error:", e);
          }
        };

        ws.onclose = () => {
          if (isSubscribed) {
            setState(prev => ({ ...prev, isConnected: false, isStreaming: false }));
          }
        };

      } catch (err: any) {
        if (isSubscribed) {
          setState(prev => ({ ...prev, error: err.message || "WebRTC init failed" }));
        }
      }
    }

    initWebRtc();

    return () => {
      isSubscribed = false;
      if (localMicStreamRef.current) {
        localMicStreamRef.current.getTracks().forEach(t => t.stop());
      }
      if (pcRef.current) {
        pcRef.current.close();
      }
      if (wsRef.current) {
        wsRef.current.close();
      }
    };
  }, [deviceId, WS_BASE, sendSignalingMessage]);

  return {
    videoRef,
    state,
    sendCommand,
    toggleMic,
  };
}
