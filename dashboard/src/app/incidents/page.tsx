"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle, Clock, RefreshCw } from "lucide-react";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://127.0.0.1:8000";

interface IncidentItem {
  id: number;
  severity: string;
  source: string;
  description: string;
  affected_device_id: string;
  status: string;
  created_at: string;
}

export default function IncidentsPage() {
  const [incidents, setIncidents] = useState<IncidentItem[]>([]);
  const [loading, setLoading] = useState(true);

  const loadIncidents = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/v1/incidents`);
      if (res.ok) {
        const data = await res.json();
        setIncidents(data);
      }
    } catch (e) {
      console.error(e);
    }
    setLoading(false);
  };

  useEffect(() => {
    loadIncidents();
  }, []);

  const updateStatus = async (id: number, newStatus: string) => {
    try {
      const res = await fetch(`${API_BASE}/api/v1/incidents/${id}?new_status=${newStatus}`, {
        method: "PATCH",
      });
      if (res.ok) {
        loadIncidents();
      }
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div style={{ maxWidth: 900, margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: "1.8rem", fontWeight: 700, display: "flex", alignItems: "center", gap: 10 }}>
            <AlertTriangle size={24} color="#ff5252" /> Operational Incidents & Health Alerts
          </h1>
          <p style={{ color: "#8b949e", marginTop: 4, fontSize: "0.95rem" }}>
            Deduplicated fleet warnings, thermal thresholds, storage exhaustion, and connectivity alarms
          </p>
        </div>

        <button className="btn btn-secondary" onClick={loadIncidents}>
          <RefreshCw size={15} /> Refresh
        </button>
      </div>

      {incidents.length === 0 ? (
        <div className="card" style={{ textAlign: "center", padding: "50px 20px" }}>
          <CheckCircle size={40} color="#00e676" style={{ margin: "0 auto 14px" }} />
          <h3 style={{ fontSize: "1.2rem", color: "#fff", marginBottom: 6 }}>All Systems Operational</h3>
          <p style={{ color: "#8b949e", fontSize: "0.9rem" }}>No active incidents or fleet warnings recorded.</p>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {incidents.map((inc) => (
            <div key={inc.id} className="card" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "16px 20px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                <div style={{
                  background: inc.severity === "CRITICAL" ? "rgba(255, 82, 82, 0.2)" : "rgba(251, 191, 36, 0.2)",
                  padding: 10,
                  borderRadius: 8
                }}>
                  <AlertTriangle size={20} color={inc.severity === "CRITICAL" ? "#ff5252" : "#fbbf24"} />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#fff", fontSize: "1rem" }}>
                    {inc.description}
                  </div>
                  <div style={{ fontSize: "0.8rem", color: "#8b949e", marginTop: 4, display: "flex", gap: 12 }}>
                    <span>Device: <code style={{ color: "#38bdf8" }}>{inc.affected_device_id || "Fleet Global"}</code></span>
                    <span>Source: {inc.source}</span>
                    <span>Status: <strong style={{ color: inc.status === "OPEN" ? "#ff5252" : "#00e676" }}>{inc.status}</strong></span>
                  </div>
                </div>
              </div>

              {inc.status === "OPEN" && (
                <button
                  className="btn btn-primary"
                  onClick={() => updateStatus(inc.id, "RESOLVED")}
                  style={{ padding: "6px 12px", fontSize: "0.8rem" }}
                >
                  Mark Resolved
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
