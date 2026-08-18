import os
from pathlib import Path
import uuid
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.devices import Device
from app.models.events import MotionEvent
from app.services.telegram_notifier import telegram_notifier

router = APIRouter(prefix="/motion", tags=["Motion Detection & Events"])

SNAPSHOT_DIR = str(Path(__file__).resolve().parent.parent.parent / "uploads" / "snapshots")
os.makedirs(SNAPSHOT_DIR, exist_ok=True)

@router.post("/event")
async def post_motion_event(
    device_id: str = Form(...),
    event_type: str = Form("MOTION_DETECTED"),
    confidence: float = Form(1.0),
    metadata: Optional[str] = Form(None),
    snapshot: Optional[UploadFile] = File(None),
    db: AsyncSession = Depends(get_db)
):
    result = await db.execute(select(Device).where(Device.device_id == device_id))
    device = result.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
        
    snapshot_url = None
    snapshot_bytes = None
    if snapshot:
        snapshot_bytes = await snapshot.read()
        filename = f"{device_id}_{uuid.uuid4().hex[:8]}_{int(datetime.now(timezone.utc).timestamp())}.jpg"
        filepath = os.path.join(SNAPSHOT_DIR, filename)
        with open(filepath, "wb") as f:
            f.write(snapshot_bytes)
        snapshot_url = f"/uploads/snapshots/{filename}"
        
    event = MotionEvent(
        device_db_id=device.id,
        event_type=event_type,
        confidence=confidence,
        thumbnail_path=snapshot_url,
        started_at=datetime.now(timezone.utc)
    )
    db.add(event)
    await db.commit()
    await db.refresh(event)
    
    # Dispatch Telegram instant notification
    ts_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    await telegram_notifier.send_motion_alert(
        device_name=device.name,
        device_id=device.device_id,
        timestamp_str=ts_str,
        snapshot_bytes=snapshot_bytes
    )
    
    return {
        "status": "ok",
        "event_id": event.id,
        "device_id": device_id,
        "snapshot_url": snapshot_url,
        "timestamp": event.started_at.isoformat()
    }

@router.get("/history")
async def get_motion_history(
    device_id: Optional[str] = None,
    limit: int = 50,
    db: AsyncSession = Depends(get_db)
):
    query = select(MotionEvent, Device.device_id, Device.name).join(Device, MotionEvent.device_db_id == Device.id)
    if device_id:
        query = query.where(Device.device_id == device_id)
    query = query.order_by(MotionEvent.started_at.desc()).limit(limit)
    
    res = await db.execute(query)
    rows = res.all()
    
    return [
        {
            "id": row[0].id,
            "device_id": row[1],
            "device_name": row[2],
            "event_type": row[0].event_type,
            "confidence": row[0].confidence,
            "snapshot_url": row[0].thumbnail_path,
            "timestamp": row[0].started_at.isoformat() if row[0].started_at else None
        }
        for row in rows
    ]
