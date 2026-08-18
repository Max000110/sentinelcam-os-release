"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { fetchDevice, fetchLatestTelemetry, Device, TelemetryData } from "../../../lib/api";
import { useWebRtcStream } from "../../../hooks/useWebRtcStream";
import { WebRtcPlayer } from "../../../components/WebRtcPlayer";
import { CanvasAiOverlay, DetectionBox } from "../../../components/CanvasAiOverlay";
import { DeviceHealthCard } from "../../../components/DeviceHealthCard";
import { CameraControls } from "../../../components/CameraControls";
import { ArrowLeft, Sliders, ShieldAlert } from "lucide-react";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || `${API_BASE}`;

export default function CameraLivePage() {
  const params = useParams();
  const deviceId = params.id as string;

  const [device, setDevice] = useState<Device | null>(null);
  const [telemetry, setTelemetry] = useState<TelemetryData | null>(null);
  const [zones, setZones] = useState<Array<{ name: string; type: string; polygon: number[][] }>>([]);
  const [aiOverlayEnabled, setAiOverlayEnabled] = useState(true);
  const [liveDetections, setLiveDetections] = useState<DetectionBox[]>([
    {
      trackId: 12,
      objectClass: "person",
      confidence: 0.94,
      bbox: { x: 0.28, y: 0.22, w: 0.22, h: 0.55 },
      isKnownFace: true,
      faceName: "Authorized Person"
    }
  ]);

  const { videoRef, state, sendCommand, toggleMic } = useWebRtcStream(deviceId);

  useEffect(() => {
    async function loadData() {
      if (!deviceId) return;
      const dev = await fetchDevice(deviceId);
      setDevice(dev);

      const tel = await fetchLatestTelemetry(deviceId);
      setTelemetry(tel);

      // Load zones
      try {
        const zRes = await fetch(`${API_BASE}/api/v1/devices/${deviceId}/zones`);
        if (zRes.ok) {
          const zData = await zRes.json();
          setZones(zData);
        }
      } catch (e) {
        console.error(e);
      }
    }

    loadData();
    const interval = setInterval(async () => {
      const tel = await fetchLatestTelemetry(deviceId);
      setTelemetry(tel);
    }, 10000);

    return () => clearInterval(interval);
  }, [deviceId]);

  return (
    <div style={{ maxWidth: 1050, margin: "0 auto" }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16 }}>
        <Link href="/" className="btn btn-secondary" style={{ padding: "8px 12px", fontSize: "0.85rem" }}>
          <ArrowLeft size={16} /> Fleet Command
        </Link>
        <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
          <button
            className={`btn ${aiOverlayEnabled ? "btn-primary" : "btn-secondary"}`}
            onClick={() => setAiOverlayEnabled(!aiOverlayEnabled)}
            style={{ padding: "6px 12px", fontSize: "0.8rem" }}
          >
            <Sliders size={14} /> AI Overlay: {aiOverlayEnabled ? "ON" : "OFF"}
          </button>
          <div>
            <h2 style={{ fontSize: "1.3rem", fontWeight: 700, color: "#fff" }}>
              {device?.name || deviceId}
            </h2>
          </div>
        </div>
      </div>

      {/* WebRTC Video Player Container with Transparent AI Canvas Overlay */}
      <div style={{ position: "relative" }}>
        <WebRtcPlayer
          videoRef={videoRef}
          state={state}
          deviceName={device?.name || deviceId}
          onToggleMic={toggleMic}
        />
        <CanvasAiOverlay
          detections={liveDetections}
          zones={zones}
          enabled={aiOverlayEnabled}
          privacyMode={device?.torch_enabled === false && false}
        />
      </div>

      {/* Remote Hardware Controls & Intercom */}
      <CameraControls onSendCommand={sendCommand} />

      {/* Device Health & Thermal Telemetry */}
      <DeviceHealthCard telemetry={telemetry} />
    </div>
  );
}
