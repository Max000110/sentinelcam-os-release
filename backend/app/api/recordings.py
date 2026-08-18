import os
from pathlib import Path
import hashlib
import uuid
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File, Form, Request, Header
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.devices import Device
from app.models.recordings import Recording, RecordingSegment

router = APIRouter(prefix="/recordings", tags=["Recordings & Playback"])

RECORDINGS_DIR = str(Path(__file__).resolve().parent.parent.parent / "uploads" / "recordings")
os.makedirs(RECORDINGS_DIR, exist_ok=True)

@router.get("")
async def list_recordings(
    device_id: Optional[str] = None,
    mode: Optional[str] = None,
    limit: int = 50,
    db: AsyncSession = Depends(get_db)
):
    query = select(Recording, Device.device_id, Device.name).join(Device, Recording.device_db_id == Device.id)
    query = query.where(Recording.deleted_at == None)
    if device_id:
        query = query.where(Device.device_id == device_id)
    if mode:
        query = query.where(Recording.recording_mode == mode)
    query = query.order_by(Recording.start_time.desc()).limit(limit)
    
    res = await db.execute(query)
    rows = res.all()
    
    return [
        {
            "id": row[0].id,
            "device_id": row[1],
            "device_name": row[2],
            "start_time": row[0].start_time.isoformat() if row[0].start_time else None,
            "end_time": row[0].end_time.isoformat() if row[0].end_time else None,
            "duration_seconds": row[0].duration_seconds,
            "file_size_mb": round(row[0].file_size / (1024 * 1024), 2),
            "recording_mode": row[0].recording_mode,
            "is_locked": row[0].is_locked,
            "checksum": row[0].checksum,
            "status": row[0].status,
            "play_url": f"/api/v1/recordings/{row[0].id}/play"
        }
        for row in rows
    ]

@router.get("/{recording_id}")
async def get_recording_detail(recording_id: int, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Recording).where(Recording.id == recording_id))
    rec = result.scalars().first()
    if not rec:
        raise HTTPException(status_code=404, detail="Recording not found")
    return rec

@router.patch("/{recording_id}/lock")
async def toggle_recording_lock(recording_id: int, locked: bool, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Recording).where(Recording.id == recording_id))
    rec = result.scalars().first()
    if not rec:
        raise HTTPException(status_code=404, detail="Recording not found")
    rec.is_locked = locked
    await db.commit()
    return {"id": rec.id, "is_locked": rec.is_locked}

@router.post("/upload")
async def upload_recording_segment(
    device_id: str = Form(...),
    start_time: str = Form(...),
    duration_seconds: float = Form(...),
    recording_mode: str = Form("MOTION_TRIGGERED"),
    video_file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db)
):
    dev_res = await db.execute(select(Device).where(Device.device_id == device_id))
    device = dev_res.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    content = await video_file.read()
    file_size = len(content)
    sha256_hash = hashlib.sha256(content).hexdigest()

    filename = f"{device_id}_{int(datetime.now().timestamp())}_{uuid.uuid4().hex[:6]}.mp4"
    filepath = os.path.join(RECORDINGS_DIR, filename)
    with open(filepath, "wb") as f:
        f.write(content)

    rec = Recording(
        device_db_id=device.id,
        start_time=datetime.fromisoformat(start_time.replace("Z", "+00:00")),
        duration_seconds=duration_seconds,
        file_size=file_size,
        file_path=filepath,
        recording_mode=recording_mode,
        status="COMPLETED",
        checksum=sha256_hash,
        is_locked=False
    )
    db.add(rec)
    await db.commit()
    await db.refresh(rec)
    
    return {
        "recording_id": rec.id,
        "checksum": sha256_hash,
        "file_size": file_size,
        "status": "COMPLETED"
    }

@router.get("/{recording_id}/play")
async def stream_recording_playback(
    recording_id: int,
    request: Request,
    range: Optional[str] = Header(None),
    db: AsyncSession = Depends(get_db)
):
    result = await db.execute(select(Recording).where(Recording.id == recording_id))
    rec = result.scalars().first()
    if not rec or not rec.file_path or not os.path.exists(rec.file_path):
        raise HTTPException(status_code=404, detail="Recording video file not found")

    path = rec.file_path
    file_size = os.path.getsize(path)

    # HTTP Range byte streaming
    if range:
        range_header = range.strip().lower()
        if range_header.startswith("bytes="):
            byte_range = range_header[6:].split("-")
            start = int(byte_range[0])
            end = int(byte_range[1]) if byte_range[1] else file_size - 1
            length = (end - start) + 1

            def iter_file_range():
                with open(path, "rb") as f:
                    f.seek(start)
                    bytes_left = length
                    while bytes_left > 0:
                        chunk_size = min(bytes_left, 64 * 1024)
                        data = f.read(chunk_size)
                        if not data:
                            break
                        bytes_left -= len(data)
                        yield data

            headers = {
                "Content-Range": f"bytes {start}-{end}/{file_size}",
                "Accept-Ranges": "bytes",
                "Content-Length": str(length),
                "Content-Type": "video/mp4",
            }
            return StreamingResponse(iter_file_range(), status_code=206, headers=headers)

    def iter_full_file():
        with open(path, "rb") as f:
            while chunk := f.read(64 * 1024):
                yield chunk

    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(file_size),
        "Content-Type": "video/mp4",
    }
    return StreamingResponse(iter_full_file(), headers=headers)

@router.delete("/{recording_id}")
async def delete_recording(recording_id: int, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Recording).where(Recording.id == recording_id))
    rec = result.scalars().first()
    if not rec:
        raise HTTPException(status_code=404, detail="Recording not found")
        
    if rec.file_path and os.path.exists(rec.file_path):
        try:
            os.remove(rec.file_path)
        except Exception:
            pass
            
    rec.deleted_at = datetime.now(timezone.utc)
    rec.status = "DELETED_BY_USER"
    await db.commit()
    return {"status": "ok", "deleted_id": recording_id}
