"use client";

import { useEffect, useState } from "react";
import { Film, Lock, Unlock, Download, Play, Trash2, RefreshCw } from "lucide-react";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || `${API_BASE}`;

interface RecordingItem {
  id: number;
  device_id: string;
  device_name: string;
  start_time: string;
  end_time: string;
  duration_seconds: number;
  file_size_mb: number;
  recording_mode: string;
  is_locked: boolean;
  checksum: string;
  status: string;
  play_url: string;
}

export default function RecordingsPage() {
  const [recordings, setRecordings] = useState<RecordingItem[]>([]);
  const [selectedRecording, setSelectedRecording] = useState<RecordingItem | null>(null);
  const [loading, setLoading] = useState(true);

  const loadRecordings = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/v1/recordings", { cache: "no-store" });
      if (res.ok) {
        const data = await res.json();
        setRecordings(data);
      }
    } catch (e) {
      console.error(e);
    }
    setLoading(false);
  };

  const toggleLock = async (id: number, currentLocked: boolean) => {
    try {
      const res = await fetch(`${API_BASE}/api/v1/recordings/${id}/lock?locked=${!currentLocked}`, {
        method: "PATCH",
      });
      if (res.ok) {
        loadRecordings();
      }
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    loadRecordings();
  }, []);

  return (
    <div style={{ maxWidth: 1100, margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: "1.8rem", fontWeight: 700, display: "flex", alignItems: "center", gap: 10 }}>
            <Film size={24} color="#38bdf8" /> Segmented CCTV Recordings
          </h1>
          <p style={{ color: "#8b949e", marginTop: 4, fontSize: "0.95rem" }}>
            Local-first MP4 segments with HTTP byte-range playback & SHA-256 checksum integrity
          </p>
        </div>

        <button className="btn btn-secondary" onClick={loadRecordings}>
          <RefreshCw size={15} /> Refresh
        </button>
      </div>

      {/* Video Playback Modal */}
      {selectedRecording && (
        <div className="card" style={{ marginBottom: 24, border: "1px solid #38bdf8" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
            <h3 style={{ fontSize: "1.1rem", color: "#fff" }}>
              Playing: {selectedRecording.device_name} ({new Date(selectedRecording.start_time).toLocaleString()})
            </h3>
            <button className="btn btn-secondary" onClick={() => setSelectedRecording(null)}>
              Close Player
            </button>
          </div>
          <div style={{ background: "#000", borderRadius: 8, overflow: "hidden", aspectRatio: "16 / 9" }}>
            <video
              src={${API_BASE}${selectedRecording.play_url}`}
              controls
              autoPlay
              style={{ width: "100%", height: "100%" }}
            />
          </div>
          <div style={{ fontSize: "0.8rem", color: "#8b949e", marginTop: 8 }}>
            SHA-256 Checksum: <code style={{ color: "#38bdf8" }}>{selectedRecording.checksum}</code>
          </div>
        </div>
      )}

      {/* Recordings List */}
      {loading ? (
        <div className="card" style={{ textAlign: "center", padding: "40px" }}>
          <p style={{ color: "#8b949e" }}>Loading recordings...</p>
        </div>
      ) : recordings.length === 0 ? (
        <div className="card" style={{ textAlign: "center", padding: "60px 20px" }}>
          <Film size={48} color="#586069" style={{ margin: "0 auto 16px" }} />
          <h3 style={{ fontSize: "1.2rem", color: "#fff", marginBottom: 8 }}>No Video Recordings Found</h3>
          <p style={{ color: "#8b949e", fontSize: "0.9rem" }}>
            The Android node will write segmented MP4 clips when motion or continuous recording is active.
          </p>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {recordings.map((rec) => (
            <div
              key={rec.id}
              className="card"
              style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "16px 20px" }}
            >
              <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
                <div style={{ background: "rgba(56, 189, 248, 0.15)", padding: 10, borderRadius: 8 }}>
                  <Film size={20} color="#38bdf8" />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#fff", fontSize: "1rem" }}>
                    {rec.device_name} • <span style={{ color: "#38bdf8" }}>{rec.recording_mode}</span>
                  </div>
                  <div style={{ fontSize: "0.8rem", color: "#8b949e", marginTop: 4 }}>
                    Start: {new Date(rec.start_time).toLocaleString()} • Duration: {rec.duration_seconds}s • Size: {rec.file_size_mb} MB
                  </div>
                </div>
              </div>

              <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
                <button
                  className="btn btn-primary"
                  onClick={() => setSelectedRecording(rec)}
                  style={{ padding: "8px 14px", fontSize: "0.85rem" }}
                >
                  <Play size={14} /> Play
                </button>

                <button
                  className={`btn ${rec.is_locked ? "btn-primary" : "btn-secondary"}`}
                  onClick={() => toggleLock(rec.id, rec.is_locked)}
                  title={rec.is_locked ? "Locked against retention deletion" : "Unlocked"}
                  style={{ padding: "8px 12px" }}
                >
                  {rec.is_locked ? <Lock size={14} /> : <Unlock size={14} />}
                </button>

                <a
                  href={${API_BASE}${rec.play_url}`}
                  download
                  className="btn btn-secondary"
                  style={{ padding: "8px 12px" }}
                  title="Download MP4 Segment"
                >
                  <Download size={14} />
                </a>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
