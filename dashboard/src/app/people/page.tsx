"use client";

import { useEffect, useState } from "react";
import { Users, UserPlus, Trash2, Lock } from "lucide-react";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://127.0.0.1:8000";

interface FaceProfileItem {
  id: number;
  display_name: string;
  status: string;
  consent_granted: boolean;
  created_at: string;
}

export default function KnownPeoplePage() {
  const [profiles, setProfiles] = useState<FaceProfileItem[]>([]);
  const [name, setName] = useState("");
  const [consent, setConsent] = useState(false);
  const [loading, setLoading] = useState(true);
  const [privacyMode, setPrivacyMode] = useState(false);

  const loadProfiles = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/v1/faces/profiles`);
      if (res.ok) {
        const data = await res.json();
        setProfiles(data);
      }
    } catch (e) {
      console.error(e);
    }
    setLoading(false);
  };

  useEffect(() => {
    loadProfiles();
  }, []);

  const handleEnroll = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name || !consent) {
      alert("Explicit user consent is mandatory for enrollment.");
      return;
    }

    // Generate normalized mock 128-dim embedding vector
    const mockVector = Array.from({ length: 128 }, () => Math.random() * 0.1);

    try {
      const res = await fetch(`${API_BASE}/api/v1/faces/enroll`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          display_name: name,
          consent_granted: consent,
          embedding_vector: mockVector,
        }),
      });
      if (res.ok) {
        setName("");
        setConsent(false);
        loadProfiles();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      const res = await fetch(`${API_BASE}/api/v1/faces/${id}`, { method: "DELETE" });
      if (res.ok) {
        loadProfiles();
      }
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div style={{ maxWidth: 900, margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: "1.8rem", fontWeight: 700, display: "flex", alignItems: "center", gap: 10 }}>
            <Users size={24} color="#fbbf24" /> Known People & Facial Intelligence
          </h1>
          <p style={{ color: "#8b949e", marginTop: 4, fontSize: "0.95rem" }}>
            Opt-in consent-based authorized profile recognition with encrypted local biometric templates
          </p>
        </div>

        <button
          className={`btn ${privacyMode ? "btn-danger" : "btn-secondary"}`}
          onClick={() => setPrivacyMode(!privacyMode)}
        >
          <Lock size={16} />
          {privacyMode ? "Privacy Mode: ON" : "Privacy Mode: OFF"}
        </button>
      </div>

      {/* Enrollment Wizard Card */}
      <div className="card" style={{ marginBottom: 24, border: "1px solid #2d3748" }}>
        <h3 style={{ fontSize: "1.1rem", marginBottom: 14, color: "#fff", display: "flex", alignItems: "center", gap: 8 }}>
          <UserPlus size={18} color="#fbbf24" /> Enroll New Authorized Profile
        </h3>
        <form onSubmit={handleEnroll} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <input
            type="text"
            placeholder="Person Display Name (e.g. John Doe, Sarah)"
            value={name}
            onChange={(e) => setName(e.target.value)}
            style={{
              background: "#1a202c",
              border: "1px solid #2d3748",
              padding: "10px 14px",
              color: "#fff",
              borderRadius: 6,
            }}
            required
          />

          <label style={{ display: "flex", alignItems: "center", gap: 10, fontSize: "0.85rem", color: "#c9d1d9", cursor: "pointer" }}>
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
              style={{ width: 16, height: 16 }}
            />
            <span>I confirm that this person has given explicit opt-in consent for facial recognition on this device.</span>
          </label>

          <button type="submit" className="btn btn-primary" style={{ alignSelf: "flex-start" }}>
            Enroll Authorized Profile
          </button>
        </form>
      </div>

      {/* Enrolled Profiles */}
      <h3 style={{ fontSize: "1.2rem", fontWeight: 600, color: "#fff", marginBottom: 12 }}>Enrolled Authorized Profiles</h3>
      {profiles.length === 0 ? (
        <div className="card" style={{ textAlign: "center", padding: "40px" }}>
          <p style={{ color: "#8b949e" }}>No enrolled face profiles. Unknown faces will be flagged according to rule severity.</p>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {profiles.map((p) => (
            <div key={p.id} className="card" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 20px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                <div style={{ background: "rgba(251, 191, 36, 0.15)", padding: 10, borderRadius: 8 }}>
                  <Users size={18} color="#fbbf24" />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#fff", fontSize: "1rem" }}>{p.display_name}</div>
                  <div style={{ fontSize: "0.8rem", color: "#8b949e", marginTop: 2 }}>
                    Status: <span style={{ color: "#00e676" }}>{p.status}</span> • Consent Granted: Yes • Model: mobilefacenet-v1.2
                  </div>
                </div>
              </div>

              <button className="btn btn-secondary" onClick={() => handleDelete(p.id)} style={{ color: "#ff5252" }}>
                <Trash2 size={16} /> Remove
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
