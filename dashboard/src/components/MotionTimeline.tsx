"use client";

import { MotionEvent } from "../lib/api";
import { AlertCircle, Clock, ShieldAlert } from "lucide-react";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || `${API_BASE}`;

interface MotionTimelineProps {
  events: MotionEvent[];
}

export function MotionTimeline({ events }: MotionTimelineProps) {
  if (!events || events.length === 0) {
    return (
      <div className="card" style={{ marginTop: 20, textAlign: "center", padding: "40px 20px" }}>
        <ShieldAlert size={36} color="#586069" style={{ margin: "0 auto 12px" }} />
        <p style={{ color: "#8b949e", fontSize: "0.95rem" }}>No recent motion triggers recorded.</p>
        <span style={{ fontSize: "0.8rem", color: "#586069" }}>The local Android YUV analyzer will trigger alerts when movement is detected.</span>
      </div>
    );
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, marginTop: 20 }}>
      {events.map((evt) => (
        <div key={evt.id} className="card" style={{ padding: "14px 18px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div style={{ background: "rgba(255, 82, 82, 0.15)", padding: 8, borderRadius: 8 }}>
              <AlertCircle size={18} color="#ff5252" />
            </div>
            <div>
              <div style={{ fontWeight: 600, fontSize: "0.95rem", color: "#fff" }}>
                {evt.device_name} ({evt.device_id})
              </div>
              <div style={{ fontSize: "0.78rem", color: "#8b949e", display: "flex", alignItems: "center", gap: 6, marginTop: 2 }}>
                <Clock size={12} />
                <span>{new Date(evt.timestamp).toLocaleString()}</span>
                <span>• Confidence: {(evt.confidence * 100).toFixed(0)}%</span>
              </div>
            </div>
          </div>

          {evt.snapshot_url && (
            <a
              href={${API_BASE}${evt.snapshot_url}`}
              target="_blank"
              rel="noreferrer"
              className="btn btn-secondary"
              style={{ fontSize: "0.8rem", padding: "6px 12px" }}
            >
              View Snapshot
            </a>
          )}
        </div>
      ))}
    </div>
  );
}
