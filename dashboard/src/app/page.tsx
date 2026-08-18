"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchDevices, Device } from "../lib/api";
import { Video, PlusCircle, Battery, Flame, RefreshCw, ChevronRight } from "lucide-react";

export default function HomePage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);

  const loadDevices = async () => {
    setLoading(true);
    const devs = await fetchDevices();
    setDevices(devs);
    setLoading(false);
  };

  useEffect(() => {
    loadDevices();
    const interval = setInterval(loadDevices, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: "1.8rem", fontWeight: 700, letterSpacing: "-0.5px" }}>Live Camera Fleet</h1>
          <p style={{ color: "#8b949e", marginTop: 4, fontSize: "0.95rem" }}>
            24×7 Android CCTV Nodes streaming via WebRTC (Target Latency: 150–500ms)
          </p>
        </div>

        <button className="btn btn-secondary" onClick={loadDevices} style={{ gap: 6 }}>
          <RefreshCw size={15} /> Refresh Fleet
        </button>
      </div>

      {loading && devices.length === 0 ? (
        <div className="card" style={{ textAlign: "center", padding: "40px" }}>
          <p style={{ color: "#8b949e" }}>Loading active camera nodes...</p>
        </div>
      ) : devices.length === 0 ? (
        <div className="card" style={{ textAlign: "center", padding: "60px 20px" }}>
          <Video size={48} color="#586069" style={{ margin: "0 auto 16px" }} />
          <h3 style={{ fontSize: "1.2rem", color: "#fff", marginBottom: 8 }}>No CCTV Nodes Connected</h3>
          <p style={{ color: "#8b949e", maxWidth: 500, margin: "0 auto 20px", fontSize: "0.9rem" }}>
            Launch the SentinelCam Android app on your phone, configure the server URL, and tap Start 24×7 CCTV Node.
          </p>
        </div>
      ) : (
        <div className="grid-2">
          {devices.map((dev) => (
            <div key={dev.id} className="card" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <h3 style={{ fontSize: "1.15rem", fontWeight: 600, color: "#fff" }}>{dev.name}</h3>
                  <span style={{ fontSize: "0.8rem", color: "#8b949e" }}>ID: {dev.device_id}</span>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <span className={`live-dot ${dev.is_online ? "" : "offline"}`} />
                  <span style={{ fontSize: "0.8rem", fontWeight: 600, color: dev.is_online ? "#00e676" : "#ff5252" }}>
                    {dev.is_online ? "ONLINE" : "OFFLINE"}
                  </span>
                </div>
              </div>

              {/* Placeholder Camera Thumbnail / WebRTC Preview Card */}
              <div style={{
                background: "#000",
                borderRadius: 8,
                aspectRatio: "16 / 9",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                position: "relative",
                border: "1px solid #1f293d"
              }}>
                <Video size={32} color={dev.is_online ? "#00e676" : "#586069"} />
                <span style={{ fontSize: "0.8rem", color: "#8b949e", marginTop: 8 }}>
                  {dev.resolution} @ {dev.target_fps}fps • WebRTC DTLS-SRTP
                </span>
              </div>

              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div style={{ display: "flex", gap: 14, fontSize: "0.85rem", color: "#8b949e" }}>
                  <span>Lens: {dev.lens_facing}</span>
                  <span>Motion: {dev.motion_detection_enabled ? "ON" : "OFF"}</span>
                </div>

                <Link href={`/camera/${dev.device_id}`} className="btn btn-primary" style={{ padding: "8px 14px", fontSize: "0.85rem" }}>
                  Open Live Stream <ChevronRight size={16} />
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
