"use client";

import { Shield, Server, Radio, Zap, HelpCircle } from "lucide-react";

export default function SettingsPage() {
  return (
    <div style={{ maxWidth: 900, margin: "0 auto" }}>
      <h1 style={{ fontSize: "1.8rem", fontWeight: 700, marginBottom: 8 }}>System & Network Configuration</h1>
      <p style={{ color: "#8b949e", marginBottom: 24, fontSize: "0.95rem" }}>
        Architecture status, STUN/TURN traversal parameters, and node pairing instructions
      </p>

      <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
        <div className="card">
          <h3 style={{ fontSize: "1.1rem", fontWeight: 600, color: "#fff", display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
            <Server size={20} color="#38bdf8" /> VPS WebRTC Signaling Gateway
          </h3>
          <p style={{ color: "#8b949e", fontSize: "0.9rem", lineHeight: 1.6 }}>
            Signaling protocol: WebSocket bi-directional SDP exchange (<code style={{ color: "#00e676" }}>/ws/signaling</code>).<br />
            Backend REST API: FastAPI 3.11 with SQLite/PostgreSQL persistence & JWT authentication.
          </p>
        </div>

        <div className="card">
          <h3 style={{ fontSize: "1.1rem", fontWeight: 600, color: "#fff", display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
            <Radio size={20} color="#00e676" /> Coturn NAT Traversal (STUN / TURN)
          </h3>
          <p style={{ color: "#8b949e", fontSize: "0.9rem", lineHeight: 1.6, marginBottom: 12 }}>
            Coturn dynamically issues HMAC-SHA1 time-limited credentials for WebRTC peers behind Carrier-Grade NAT (CGNAT) and symmetric firewalls.
          </p>
          <div style={{ background: "#0d1117", padding: 14, borderRadius: 8, fontFamily: "monospace", fontSize: "0.85rem", color: "#c9d1d9" }}>
            <div>STUN Port: 3478 UDP/TCP</div>
            <div>TURNS (TLS) Port: 5349 UDP/TCP</div>
            <div>Relay UDP Range: 49152 - 65535</div>
            <div>Realm: sentinelcam.local</div>
          </div>
        </div>

        <div className="card">
          <h3 style={{ fontSize: "1.1rem", fontWeight: 600, color: "#fff", display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
            <Zap size={20} color="#fbbf24" /> Pairing a New Android CCTV Node
          </h3>
          <ol style={{ color: "#8b949e", fontSize: "0.9rem", lineHeight: 1.8, paddingLeft: 20 }}>
            <li>Install the SentinelCam APK on your Android phone.</li>
            <li>Connect phone to continuous power (or battery bypass).</li>
            <li>Enter your VPS Server URL (e.g., <code style={{ color: "#fff" }}>http://vps-ip:8000</code>).</li>
            <li>Assign a unique Device ID (e.g., <code style={{ color: "#fff" }}>cam_balcony_03</code>).</li>
            <li>Grant Camera, Audio, and Battery Optimization Exemption permissions.</li>
            <li>Tap <strong>START 24×7 CCTV NODE</strong>. The stream will appear on this dashboard immediately.</li>
          </ol>
        </div>
      </div>
    </div>
  );
}
