"use client";

import { Battery, Zap, Flame, Wifi, HardDrive, Cpu } from "lucide-react";
import { TelemetryData } from "../lib/api";

interface DeviceHealthCardProps {
  telemetry: TelemetryData | null;
}

export function DeviceHealthCard({ telemetry }: DeviceHealthCardProps) {
  const battery = telemetry?.battery_level ?? 88;
  const isCharging = telemetry?.is_charging === "AC" || telemetry?.is_charging === "USB";
  const temp = telemetry?.temperature_c ?? 32.4;
  const networkType = telemetry?.network_type ?? "WIFI";
  const rssi = telemetry?.wifi_rssi_dbm ?? -55;
  const freeStorageMb = telemetry?.storage_free_mb ?? 14200;
  const freeStorageGb = (freeStorageMb / 1024).toFixed(1);

  // Dynamic status colors
  const tempColor = temp > 42 ? "#ff5252" : temp > 38 ? "#fbbf24" : "#00e676";
  const batteryColor = battery < 20 ? "#ff5252" : "#00e676";

  return (
    <div className="card" style={{ marginTop: 20 }}>
      <h3 style={{ fontSize: "1rem", marginBottom: 14, display: "flex", alignItems: "center", gap: 8 }}>
        <Cpu size={18} color="#38bdf8" />
        <span>Android Node Telemetry & Thermal Health</span>
      </h3>

      <div className="telemetry-grid">
        <div className="telemetry-item">
          <div className="telemetry-label" style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <Battery size={14} color={batteryColor} /> Battery Status
          </div>
          <div className="telemetry-val" style={{ color: batteryColor }}>
            {battery}% {isCharging && <Zap size={14} color="#fbbf24" style={{ display: "inline" }} />}
          </div>
        </div>

        <div className="telemetry-item">
          <div className="telemetry-label" style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <Flame size={14} color={tempColor} /> Device Temp
          </div>
          <div className="telemetry-val" style={{ color: tempColor }}>
            {temp}°C
          </div>
        </div>

        <div className="telemetry-item">
          <div className="telemetry-label" style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <Wifi size={14} color="#38bdf8" /> Network ({networkType})
          </div>
          <div className="telemetry-val" style={{ fontSize: "0.95rem" }}>
            {rssi} dBm
          </div>
        </div>

        <div className="telemetry-item">
          <div className="telemetry-label" style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <HardDrive size={14} color="#a78bfa" /> Free Storage
          </div>
          <div className="telemetry-val" style={{ fontSize: "0.95rem" }}>
            {freeStorageGb} GB
          </div>
        </div>
      </div>
    </div>
  );
}
