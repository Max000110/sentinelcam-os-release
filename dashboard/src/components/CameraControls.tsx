"use client";

import { useState } from "react";
import { Flashlight, RefreshCw, Sliders, ShieldCheck } from "lucide-react";

interface CameraControlsProps {
  onSendCommand: (command: string, payload?: object) => void;
}

export function CameraControls({ onSendCommand }: CameraControlsProps) {
  const [torchOn, setTorchOn] = useState(false);
  const [guardInfo, setGuardInfo] = useState<string | null>(null);

  const handleToggleTorch = () => {
    const newState = !torchOn;
    setTorchOn(newState);
    onSendCommand("toggle_torch", { enable: newState });
  };

  const handleSwitchCamera = () => {
    onSendCommand("switch_camera");
  };

  const handleShowGuardInfo = () => {
    setGuardInfo("Local YUV Motion Analyzer active on device (Threshold: 50)");
    setTimeout(() => setGuardInfo(null), 3500);
  };

  return (
    <div className="card" style={{ marginTop: 20 }}>
      <h3 style={{ fontSize: "1rem", marginBottom: 14, display: "flex", alignItems: "center", gap: 8 }}>
        <Sliders size={18} color="#00e676" />
        <span>Remote Hardware Controls</span>
      </h3>

      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "center" }}>
        <button
          className={`btn ${torchOn ? "btn-primary" : "btn-secondary"}`}
          onClick={handleToggleTorch}
        >
          <Flashlight size={16} />
          {torchOn ? "Turn Torch OFF" : "Turn Torch ON"}
        </button>

        <button className="btn btn-secondary" onClick={handleSwitchCamera}>
          <RefreshCw size={16} />
          Flip Front/Back Lens
        </button>

        <button
          className="btn btn-secondary"
          onClick={handleShowGuardInfo}
        >
          <ShieldCheck size={16} color="#00e676" />
          Motion Guard: ACTIVE
        </button>

        {guardInfo && (
          <span style={{ fontSize: "0.85rem", color: "#00e676", marginLeft: 8 }}>
            {guardInfo}
          </span>
        )}
      </div>
    </div>
  );
}
