"use client";

import { useEffect, useRef } from "react";

export interface DetectionBox {
  trackId?: number;
  objectClass: string;
  confidence: number;
  bbox: { x: number; y: number; w: number; h: number }; // Normalized 0.0 - 1.0
  isKnownFace?: boolean;
  faceName?: string;
}

interface CanvasAiOverlayProps {
  detections: DetectionBox[];
  zones?: Array<{ name: string; type: string; polygon: number[][] }>;
  enabled: boolean;
  privacyMode?: boolean;
}

export function CanvasAiOverlay({ detections, zones = [], enabled, privacyMode = false }: CanvasAiOverlayProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Match canvas dimensions to container
    const width = canvas.offsetWidth;
    const height = canvas.offsetHeight;
    canvas.width = width;
    canvas.height = height;

    ctx.clearRect(0, 0, width, height);

    if (privacyMode) {
      // Privacy Mode Watermark Overlay
      ctx.fillStyle = "rgba(0, 0, 0, 0.45)";
      ctx.fillRect(0, 0, width, height);
      ctx.fillStyle = "#ff5252";
      ctx.font = "bold 16px sans-serif";
      ctx.fillText("🔒 PRIVACY MODE ACTIVE — AI & FACIAL TRACKING DISABLED", 20, 36);
      return;
    }

    if (!enabled) return;

    // 1. Draw Polygonal Detection Zones
    zones.forEach(zone => {
      if (!zone.polygon || zone.polygon.length < 3) return;
      ctx.beginPath();
      const startX = zone.polygon[0][0] * width;
      const startY = zone.polygon[0][1] * height;
      ctx.moveTo(startX, startY);

      for (let i = 1; i < zone.polygon.length; i++) {
        ctx.lineTo(zone.polygon[i][0] * width, zone.polygon[i][1] * height);
      }
      ctx.closePath();

      const isProtected = zone.type === "PROTECTED";
      ctx.fillStyle = isProtected ? "rgba(255, 82, 82, 0.12)" : "rgba(56, 189, 248, 0.12)";
      ctx.fill();
      ctx.strokeStyle = isProtected ? "#ff5252" : "#38bdf8";
      ctx.lineWidth = 2;
      ctx.stroke();

      // Zone Label
      ctx.fillStyle = isProtected ? "#ff5252" : "#38bdf8";
      ctx.font = "bold 12px sans-serif";
      ctx.fillText(`⛉ ${zone.name}`, startX + 6, startY + 16);
    });

    // 2. Draw Object Detections & Bounding Boxes
    detections.forEach(det => {
      const bx = det.bbox.x * width;
      const by = det.bbox.y * height;
      const bw = det.bbox.w * width;
      const bh = det.bbox.h * height;

      // Color scheme
      let strokeColor = "#00e676"; // Default person green
      if (det.objectClass === "car" || det.objectClass === "truck") strokeColor = "#38bdf8";
      if (det.isKnownFace) strokeColor = "#fbbf24";

      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = 2.5;
      ctx.strokeRect(bx, by, bw, bh);

      // Label background
      const labelText = det.faceName 
        ? `👤 ${det.faceName} (${(det.confidence * 100).toFixed(0)}%)`
        : `[${det.trackId ? '#' + det.trackId + ' ' : ''}${det.objectClass.toUpperCase()}] ${(det.confidence * 100).toFixed(0)}%`;

      ctx.font = "bold 11px sans-serif";
      const textMetrics = ctx.measureText(labelText);
      const textWidth = textMetrics.width + 12;

      ctx.fillStyle = strokeColor;
      ctx.fillRect(bx, Math.max(0, by - 20), textWidth, 20);

      ctx.fillStyle = "#000000";
      ctx.fillText(labelText, bx + 6, Math.max(14, by - 5));
    });

  }, [detections, zones, enabled, privacyMode]);

  return (
    <canvas
      ref={canvasRef}
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        pointerEvents: "none",
        zIndex: 10,
      }}
    />
  );
}
