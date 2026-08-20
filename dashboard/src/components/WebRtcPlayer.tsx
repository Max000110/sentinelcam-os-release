"use client";

import { useState, useEffect } from "react";
import { Maximize2, Mic, MicOff, Volume2, VolumeX, Camera } from "lucide-react";
import { StreamState } from "../hooks/useWebRtcStream";

interface WebRtcPlayerProps {
  videoRef: React.RefObject<HTMLVideoElement>;
  state: StreamState;
  deviceName: string;
  onToggleMic: () => void;
}

export function WebRtcPlayer({ videoRef, state, deviceName, onToggleMic }: WebRtcPlayerProps) {
  const [isMuted, setIsMuted] = useState(false);

  // Zero-drift ultra-low-latency video sync mechanism
  // If the browser video element buffers more than 20ms behind real-time, catch up immediately
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const checkDrift = () => {
      if (video.buffered.length > 0) {
        const bufferedEnd = video.buffered.end(video.buffered.length - 1);
        const drift = bufferedEnd - video.currentTime;
        if (drift > 0.020) { // More than 20ms drift behind real-time
          video.currentTime = bufferedEnd;
        }
      }
    };

    const interval = setInterval(checkDrift, 150);
    return () => clearInterval(interval);
  }, [videoRef]);

  const toggleMute = () => {
    if (videoRef.current) {
      videoRef.current.muted = !videoRef.current.muted;
      setIsMuted(videoRef.current.muted);
    }
  };

  const captureSnapshot = () => {
    if (!videoRef.current) return;
    const video = videoRef.current;
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
    const ctx = canvas.getContext("2d");
    if (ctx) {
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      const dataUrl = canvas.toDataURL("image/jpeg");
      const a = document.createElement("a");
      a.href = dataUrl;
      a.download = `snapshot_${deviceName.replace(/\s+/g, "_")}_${Date.now()}.jpg`;
      a.click();
    }
  };

  const toggleFullscreen = () => {
    if (videoRef.current) {
      if (document.fullscreenElement) {
        document.exitFullscreen();
      } else {
        videoRef.current.requestFullscreen();
      }
    }
  };

  return (
    <div className="video-container">
      <video
        ref={videoRef}
        autoPlay
        playsInline
        className="video-stream"
        muted={isMuted}
        disablePictureInPicture
        disableRemotePlayback
        style={{ objectFit: "cover" }}
      />

      {/* Stream Status Overlay Badge */}
      <div className="video-overlay-badge">
        <span className={`live-dot ${state.nodeOnline ? "" : "offline"}`} />
        <span>{state.nodeOnline ? "WEBRTC LIVE" : "NODE OFFLINE"}</span>
        {state.nodeOnline && (
          <span style={{ color: "#38bdf8", marginLeft: 4 }}>
            ⚡ {state.latencyMs}ms
          </span>
        )}
      </div>

      {/* Floating Controls Overlay */}
      <div className="video-controls">
        <button
          className={`btn-icon ${state.isMicActive ? "active" : ""}`}
          onClick={onToggleMic}
          title={state.isMicActive ? "Mute Intercom Mic" : "Push-to-Talk Intercom"}
        >
          {state.isMicActive ? <Mic size={18} /> : <MicOff size={18} />}
        </button>

        <button className="btn-icon" onClick={toggleMute} title="Mute/Unmute Audio">
          {isMuted ? <VolumeX size={18} /> : <Volume2 size={18} />}
        </button>

        <button className="btn-icon" onClick={captureSnapshot} title="Capture Snapshot">
          <Camera size={18} />
        </button>

        <button className="btn-icon" onClick={toggleFullscreen} title="Fullscreen">
          <Maximize2 size={18} />
        </button>
      </div>
    </div>
  );
}
