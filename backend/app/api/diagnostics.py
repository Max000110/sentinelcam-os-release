import json
from datetime import datetime, timedelta, timezone
from typing import Dict, Any
from pydantic import BaseModel
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.devices import Device
from app.models.fleet_and_ota import DiagnosticReport

router = APIRouter(prefix="/diagnostics", tags=["Remote Diagnostics"])

class DiagnosticUpload(BaseModel):
    device_id: str
    metrics: Dict[str, Any]

@router.post("")
async def upload_diagnostics(payload: DiagnosticUpload, db: AsyncSession = Depends(get_db)):
    dev_res = await db.execute(select(Device).where(Device.device_id == payload.device_id))
    device = dev_res.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    report = DiagnosticReport(
        device_db_id=device.id,
        diagnostic_json=json.dumps(payload.metrics),
        expires_at=datetime.now(timezone.utc) + timedelta(days=7)
    )
    db.add(report)
    await db.commit()
    await db.refresh(report)
    return {"status": "ok", "report_id": report.id}

@router.get("/{report_id}")
async def get_diagnostic_report(report_id: int, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(DiagnosticReport).where(DiagnosticReport.id == report_id))
    report = res.scalars().first()
    if not report:
        raise HTTPException(status_code=404, detail="Report not found")
    return {
        "id": report.id,
        "device_db_id": report.device_db_id,
        "metrics": json.loads(report.diagnostic_json),
        "created_at": report.created_at.isoformat()
    }
