"use client";

import { useEffect, useState } from "react";
import { fetchMotionEvents, MotionEvent } from "../../lib/api";
import { MotionTimeline } from "../../components/MotionTimeline";
import { Bell, RefreshCw } from "lucide-react";

export default function EventsPage() {
  const [events, setEvents] = useState<MotionEvent[]>([]);
  const [loading, setLoading] = useState(true);

  const loadEvents = async () => {
    setLoading(true);
    const data = await fetchMotionEvents();
    setEvents(data);
    setLoading(false);
  };

  useEffect(() => {
    loadEvents();
  }, []);

  return (
    <div style={{ maxWidth: 900, margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: "1.8rem", fontWeight: 700, display: "flex", alignItems: "center", gap: 10 }}>
            <Bell size={24} color="#ff5252" /> Motion Event History
          </h1>
          <p style={{ color: "#8b949e", marginTop: 4, fontSize: "0.95rem" }}>
            Alerts detected by on-device YUV Luminance Analyzers and Telegram triggers
          </p>
        </div>

        <button className="btn btn-secondary" onClick={loadEvents}>
          <RefreshCw size={15} /> Refresh
        </button>
      </div>

      {loading ? (
        <div className="card" style={{ textAlign: "center", padding: "40px" }}>
          <p style={{ color: "#8b949e" }}>Loading events...</p>
        </div>
      ) : (
        <MotionTimeline events={events} />
      )}
    </div>
  );
}
