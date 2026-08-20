const API_BASE = "";

export interface Device {
  id: number;
  device_id: string;
  name: string;
  api_key: string;
  resolution: string;
  target_fps: number;
  target_bitrate_kbps: number;
  lens_facing: string;
  torch_enabled: boolean;
  motion_detection_enabled: boolean;
  motion_sensitivity: number;
  is_online: boolean;
  last_seen_at?: string;
  created_at: string;
}

export interface TelemetryData {
  device_id: string;
  battery_level?: number;
  is_charging?: string;
  temperature_c?: number;
  storage_free_mb?: number;
  storage_total_mb?: number;
  network_type?: string;
  wifi_rssi_dbm?: number;
  current_fps?: number;
  current_bitrate_kbps?: number;
  uptime_seconds?: number;
  timestamp?: string;
}

export interface MotionEvent {
  id: number;
  device_id: string;
  device_name: string;
  event_type: string;
  confidence: number;
  snapshot_url?: string;
  timestamp: string;
}

export interface IceServer {
  urls: string[];
  username?: string;
  credential?: string;
}

export interface RtcConfig {
  iceServers: IceServer[];
  iceTransportPolicy?: string;
}

export async function fetchDevices(): Promise<Device[]> {
  try {
    const res = await fetch(`${API_BASE}/api/v1/devices`, { cache: "no-store" });
    if (!res.ok) throw new Error("Failed to fetch devices");
    return await res.json();
  } catch (e) {
    console.error("fetchDevices error:", e);
    return [];
  }
}

export async function fetchDevice(deviceId: string): Promise<Device | null> {
  try {
    const res = await fetch(`${API_BASE}/api/v1/devices/${deviceId}`, { cache: "no-store" });
    if (!res.ok) return null;
    return await res.json();
  } catch (e) {
    console.error("fetchDevice error:", e);
    return null;
  }
}

export async function fetchLatestTelemetry(deviceId: string): Promise<TelemetryData | null> {
  try {
    const res = await fetch(`${API_BASE}/api/v1/telemetry/${deviceId}/latest`, { cache: "no-store" });
    if (!res.ok) return null;
    return await res.json();
  } catch (e) {
    console.error("fetchLatestTelemetry error:", e);
    return null;
  }
}

export async function fetchIceServers(): Promise<RtcConfig> {
  try {
    const res = await fetch(`${API_BASE}/api/v1/stream/ice-servers`, { cache: "no-store" });
    if (!res.ok) throw new Error("Failed to fetch ICE servers");
    return await res.json();
  } catch (e) {
    console.warn("Using fallback public STUN servers:", e);
    return {
      iceServers: [{ urls: ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"] }],
    };
  }
}

export async function fetchMotionEvents(deviceId?: string): Promise<MotionEvent[]> {
  try {
    const url = deviceId
      ? `${API_BASE}/api/v1/motion/history?device_id=${deviceId}`
      : `${API_BASE}/api/v1/motion/history`;
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) throw new Error("Failed to fetch motion history");
    return await res.json();
  } catch (e) {
    console.error("fetchMotionEvents error:", e);
    return [];
  }
}
