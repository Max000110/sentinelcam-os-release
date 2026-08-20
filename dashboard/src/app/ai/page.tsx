"use client";

import { useEffect, useState } from "react";
import { Sliders, Plus, Trash2 } from "lucide-react";
import { fetchDevices, Device } from "../../lib/api";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://127.0.0.1:8000";

interface ZoneItem {
  id: number;
  name: string;
  zone_type: string;
  polygon: number[][];
  is_active: boolean;
}

export default function AiZonesPage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>("");
  const [zones, setZones] = useState<ZoneItem[]>([]);
  const [newZoneName, setNewZoneName] = useState("");
  const [newZoneType, setNewZoneType] = useState("PROTECTED");

  useEffect(() => {
    async function load() {
      const devs = await fetchDevices();
      setDevices(devs);
      if (devs.length > 0) {
        setSelectedDeviceId(devs[0].device_id);
      }
    }
    load();
  }, []);

  const loadZones = async (deviceId: string) => {
    if (!deviceId) return;
    try {
      const res = await fetch(`${API_BASE}/api/v1/devices/${deviceId}/zones`);
      if (res.ok) {
        const data = await res.json();
        setZones(data);
      }
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    if (selectedDeviceId) {
      loadZones(selectedDeviceId);
    }
  }, [selectedDeviceId]);

  const handleCreateZone = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newZoneName || !selectedDeviceId) return;

    // Default 4-point polygon (e.g. 20% to 60% box)
    const polygon = [
      [0.2, 0.2],
      [0.6, 0.2],
      [0.6, 0.7],
      [0.2, 0.7],
    ];

    try {
      const res = await fetch(`${API_BASE}/api/v1/devices/${selectedDeviceId}/zones`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: newZoneName,
          zone_type: newZoneType,
          polygon,
        }),
      });
      if (res.ok) {
        setNewZoneName("");
        loadZones(selectedDeviceId);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleDeleteZone = async (id: number) => {
    try {
      const res = await fetch(`${API_BASE}/api/v1/zones/${id}`, { method: "DELETE" });
      if (res.ok) {
        loadZones(selectedDeviceId);
      }
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div style={{ maxWidth: 1000, margin: "0 auto" }}>
      <h1 style={{ fontSize: "1.8rem", fontWeight: 700, display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
        <Sliders size={24} color="#00e676" /> AI Detection Zones & Rules
      </h1>
      <p style={{ color: "#8b949e", marginBottom: 24, fontSize: "0.95rem" }}>
        Define polygonal protected zones, ignored noise areas, and automated severity rules
      </p>

      {/* Camera Selector */}
      <div className="card" style={{ marginBottom: 20 }}>
        <label style={{ display: "block", fontSize: "0.85rem", color: "#8b949e", marginBottom: 8 }}>Select CCTV Node:</label>
        <select
          value={selectedDeviceId}
          onChange={(e) => setSelectedDeviceId(e.target.value)}
          style={{
            background: "#1a202c",
            color: "#fff",
            border: "1px solid #2d3748",
            padding: "8px 14px",
            borderRadius: 6,
            width: "100%",
            fontSize: "0.95rem"
          }}
        >
          {devices.map(d => (
            <option key={d.device_id} value={d.device_id}>{d.name} ({d.device_id})</option>
          ))}
        </select>
      </div>

      {/* Add Zone Form */}
      <div className="card" style={{ marginBottom: 24 }}>
        <h3 style={{ fontSize: "1.1rem", marginBottom: 14, color: "#fff", display: "flex", alignItems: "center", gap: 8 }}>
          <Plus size={18} color="#00e676" /> Create Polygonal Detection Zone
        </h3>
        <form onSubmit={handleCreateZone} style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
          <input
            type="text"
            placeholder="Zone Name (e.g. Front Porch, Driveway)"
            value={newZoneName}
            onChange={(e) => setNewZoneName(e.target.value)}
            style={{
              flex: 1,
              background: "#1a202c",
              border: "1px solid #2d3748",
              padding: "10px 14px",
              color: "#fff",
              borderRadius: 6,
            }}
            required
          />
          <select
            value={newZoneType}
            onChange={(e) => setNewZoneType(e.target.value)}
            style={{
              background: "#1a202c",
              border: "1px solid #2d3748",
              padding: "10px 14px",
              color: "#fff",
              borderRadius: 6,
            }}
          >
            <option value="PROTECTED">PROTECTED (Triggers High Severity Alerts)</option>
            <option value="IGNORED">IGNORED (Suppresses Movement & Flags)</option>
            <option value="MONITOR">MONITOR (Logs for Analytics)</option>
          </select>
          <button type="submit" className="btn btn-primary">
            Add Zone
          </button>
        </form>
      </div>

      {/* Active Zones List */}
      <h3 style={{ fontSize: "1.2rem", fontWeight: 600, color: "#fff", marginBottom: 12 }}>Active Zones for Node</h3>
      {zones.length === 0 ? (
        <div className="card" style={{ textAlign: "center", padding: "40px" }}>
          <p style={{ color: "#8b949e" }}>No detection zones defined for this node.</p>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {zones.map((z) => (
            <div key={z.id} className="card" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 20px" }}>
              <div>
                <div style={{ fontWeight: 600, fontSize: "1rem", color: "#fff" }}>
                  {z.name} • <span style={{ color: z.zone_type === "PROTECTED" ? "#ff5252" : "#38bdf8" }}>{z.zone_type}</span>
                </div>
                <div style={{ fontSize: "0.8rem", color: "#8b949e", marginTop: 4 }}>
                  Vertices: {z.polygon.length} points • Coordinates: {JSON.stringify(z.polygon)}
                </div>
              </div>
              <button className="btn btn-secondary" onClick={() => handleDeleteZone(z.id)} style={{ color: "#ff5252" }}>
                <Trash2 size={16} /> Delete
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
