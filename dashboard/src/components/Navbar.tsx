"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Shield, Video, Film, Bell, Sliders, Users, AlertTriangle, Settings } from "lucide-react";

export function Navbar() {
  const pathname = usePathname();

  return (
    <nav className="navbar">
      <Link href="/" className="brand">
        <Shield size={24} color="#00e676" />
        <span>SentinelCam</span>
        <span className="brand-badge">Command Center</span>
      </Link>

      <div className="nav-links">
        <Link href="/" className={`nav-link ${pathname === "/" ? "active" : ""}`}>
          <Video size={16} style={{ display: "inline", marginRight: 6, verticalAlign: "text-bottom" }} />
          Fleet
        </Link>
        <Link href="/recordings" className={`nav-link ${pathname === "/recordings" ? "active" : ""}`}>
          <Film size={16} style={{ display: "inline", marginRight: 6, verticalAlign: "text-bottom" }} />
          Recordings
        </Link>
        <Link href="/events" className={`nav-link ${pathname === "/events" ? "active" : ""}`}>
          <Bell size={16} style={{ display: "inline", marginRight: 6, verticalAlign: "text-bottom" }} />
          Events
        </Link>
        <Link href="/ai" className={`nav-link ${pathname === "/ai" ? "active" : ""}`}>
          <Sliders size={16} style={{ display: "inline", marginRight: 6, verticalAlign: "text-bottom" }} />
          AI & Zones
        </Link>
        <Link href="/people" className={`nav-link ${pathname === "/people" ? "active" : ""}`}>
          <Users size={16} style={{ display: "inline", marginRight: 6, verticalAlign: "text-bottom" }} />
          Known People
        </Link>
        <Link href="/incidents" className={`nav-link ${pathname === "/incidents" ? "active" : ""}`}>
          <AlertTriangle size={16} style={{ display: "inline", marginRight: 6, verticalAlign: "text-bottom" }} />
          Incidents
        </Link>
        <Link href="/settings" className={`nav-link ${pathname === "/settings" ? "active" : ""}`}>
          <Settings size={16} style={{ display: "inline", marginRight: 6, verticalAlign: "text-bottom" }} />
          Settings
        </Link>
      </div>
    </nav>
  );
}
