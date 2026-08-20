import json
import logging
import os
from pathlib import Path
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File, Form
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.devices import Device
from app.models.events import AiEvent, EventSeverity
from app.models.zones_and_rules import Zone, Rule
from app.models.fleet_and_ota import OtaRelease
from app.services.ai_rule_engine import ai_rule_engine
from app.services.telegram_notifier import telegram_notifier

router = APIRouter(prefix="/ai", tags=["AI & Object Intelligence"])
logger = logging.getLogger("sentinelcam.ai_api")

import re

MAX_AI_SNAPSHOT_SIZE = 10 * 1024 * 1024  # 10 MB

@router.post("/events")
async def ingest_ai_event(
    device_id: str = Form(...),
    object_class: str = Form("person"), # person, car, motorcycle, bicycle
    confidence: float = Form(0.90),
    bbox_json: str = Form(...), # {"x":0.2, "y":0.3, "w":0.2, "h":0.4}
    track_id: Optional[int] = Form(None),
    model_name: str = Form("yolov8n-tflite"),
    snapshot: Optional[UploadFile] = File(None),
    db: AsyncSession = Depends(get_db)
):
    if not re.match(r"^[a-zA-Z0-9_-]+$", device_id):
        raise HTTPException(status_code=400, detail="Invalid device_id format")

    dev_res = await db.execute(select(Device).where(Device.device_id == device_id))
    device = dev_res.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    # Load active zones and rules for this device
    zones_res = await db.execute(select(Zone).where(Zone.device_db_id == device.id))
    zones = zones_res.scalars().all()
    
    rules_res = await db.execute(select(Rule).where(Rule.device_db_id == device.id))
    rules = rules_res.scalars().all()

    try:
        bbox = json.loads(bbox_json)
    except Exception:
        bbox = {"x": 0.5, "y": 0.5, "w": 0.2, "h": 0.2}

    # Evaluate detection with rule engine
    eval_result = ai_rule_engine.evaluate_detection(
        device_db_id=device.id,
        object_class=object_class,
        confidence=confidence,
        bbox=bbox,
        track_id=track_id,
        zones=zones,
        rules=rules
    )

    if eval_result.get("suppressed"):
        return {"status": "suppressed", "reason": eval_result.get("reason")}

    # Save thumbnail if uploaded
    thumbnail_url = None
    snapshot_bytes = None
    if snapshot:
        snapshot_bytes = await snapshot.read()
        if len(snapshot_bytes) > MAX_AI_SNAPSHOT_SIZE:
            raise HTTPException(status_code=413, detail="Snapshot image exceeds 10MB limit")

        safe_device_id = re.sub(r"[^a-zA-Z0-9_-]", "", device_id)
        filename = f"ai_{safe_device_id}_{int(datetime.now(timezone.utc).timestamp())}.jpg"
        snapshots_dir = (Path(__file__).resolve().parent.parent.parent / "uploads" / "snapshots").resolve()
        target_path = (snapshots_dir / filename).resolve()
        if not target_path.is_relative_to(snapshots_dir):
            raise HTTPException(status_code=400, detail="Invalid target path")

        filepath = str(target_path)
        with open(filepath, "wb") as f:
            f.write(snapshot_bytes)
        thumbnail_url = f"/uploads/snapshots/{filename}"

    ai_event = AiEvent(
        device_db_id=device.id,
        event_type=eval_result["event_type"],
        severity=eval_result["severity"],
        object_class=object_class,
        track_id=track_id,
        zone_id=eval_result.get("zone_id"),
        max_confidence=confidence,
        avg_confidence=confidence,
        bbox_json=bbox_json,
        model_name=model_name,
        thumbnail_path=thumbnail_url,
        status="CONFIRMED"
    )
    db.add(ai_event)
    await db.commit()
    await db.refresh(ai_event)

    # Dispatch notification if rule specifies
    if eval_result.get("should_notify"):
        ts_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        await telegram_notifier.send_motion_alert(
            device_name=f"{device.name} [AI: {object_class.upper()}]",
            device_id=device.device_id,
            timestamp_str=ts_str,
            snapshot_bytes=snapshot_bytes
        )

    return {
        "status": "ok",
        "event_id": ai_event.id,
        "event_type": ai_event.event_type,
        "severity": ai_event.severity.value,
        "thumbnail_url": thumbnail_url
    }

@router.get("/events")
async def list_ai_events(
    device_id: Optional[str] = None,
    severity: Optional[str] = None,
    object_class: Optional[str] = None,
    limit: int = 50,
    db: AsyncSession = Depends(get_db)
):
    query = select(AiEvent, Device.device_id, Device.name).join(Device, AiEvent.device_db_id == Device.id)
    if device_id:
        query = query.where(Device.device_id == device_id)
    if severity:
        query = query.where(AiEvent.severity == severity)
    if object_class:
        query = query.where(AiEvent.object_class == object_class)
    query = query.order_by(AiEvent.start_time.desc()).limit(limit)
    
    res = await db.execute(query)
    rows = res.all()
    
    return [
        {
            "id": row[0].id,
            "device_id": row[1],
            "device_name": row[2],
            "event_type": row[0].event_type,
            "severity": row[0].severity.value,
            "object_class": row[0].object_class,
            "confidence": row[0].max_confidence,
            "track_id": row[0].track_id,
            "zone_id": row[0].zone_id,
            "bbox": json.loads(row[0].bbox_json) if row[0].bbox_json else None,
            "thumbnail_url": row[0].thumbnail_path,
            "timestamp": row[0].start_time.isoformat() if row[0].start_time else None
        }
        for row in rows
    ]

@router.get("/models/active")
async def get_active_ai_model():
    return {
        "model_name": "yolov8n-tflite-v1.4",
        "version": "1.4.0",
        "sha256": "3a8c821b0f92b71efcf82902f23d4e8c159f80a2b8e390c427de75e54d896172",
        "supported_classes": ["person", "car", "motorcycle", "bus", "truck", "bicycle"],
        "recommended_fps": 3,
        "input_size": 320
    }
