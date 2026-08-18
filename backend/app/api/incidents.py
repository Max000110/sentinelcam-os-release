import secrets
from datetime import datetime, timedelta, timezone
from typing import List, Optional
from pydantic import BaseModel
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.incidents_and_audit import Incident
from app.models.devices import PairingCode, Device, DeviceStatusEnum

router = APIRouter(tags=["Incidents & Device Pairing"])

class PairingClaimRequest(BaseModel):
    pairing_code: str
    device_id: str
    device_name: str
    platform: str = "android"

@router.get("/incidents")
async def list_incidents(status: Optional[str] = None, db: AsyncSession = Depends(get_db)):
    query = select(Incident)
    if status:
        query = query.where(Incident.status == status)
    query = query.order_by(Incident.created_at.desc()).limit(50)
    res = await db.execute(query)
    incidents = res.scalars().all()
    return incidents

@router.patch("/incidents/{incident_id}")
async def update_incident_status(incident_id: int, new_status: str, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(Incident).where(Incident.id == incident_id))
    inc = res.scalars().first()
    if not inc:
        raise HTTPException(status_code=404, detail="Incident not found")
    inc.status = new_status
    if new_status == "ACKNOWLEDGED":
        inc.acknowledged_at = datetime.now(timezone.utc)
    elif new_status == "RESOLVED":
        inc.resolved_at = datetime.now(timezone.utc)
    await db.commit()
    return inc

@router.post("/devices/pairing/generate")
async def generate_pairing_code(device_name: str = "Android CCTV Camera", db: AsyncSession = Depends(get_db)):
    # Generate 8-character single-use code e.g. "SENT-8492"
    code_str = f"SENT-{secrets.token_hex(2).upper()}"
    p_code = PairingCode(
        user_id=1,
        code=code_str,
        device_name=device_name,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=15),
        is_used=False
    )
    db.add(p_code)
    await db.commit()
    return {
        "pairing_code": code_str,
        "expires_in_minutes": 15,
        "device_name": device_name
    }

@router.post("/devices/pairing/claim")
async def claim_pairing_code(claim: PairingClaimRequest, db: AsyncSession = Depends(get_db)):
    res = await db.execute(
        select(PairingCode).where(
            PairingCode.code == claim.pairing_code,
            PairingCode.is_used == False,
            PairingCode.expires_at > datetime.now(timezone.utc)
        )
    )
    p_code = res.scalars().first()
    if not p_code:
        raise HTTPException(status_code=400, detail="Invalid or expired pairing code")

    p_code.is_used = True

    # Register device bound to user
    dev_res = await db.execute(select(Device).where(Device.device_id == claim.device_id))
    device = dev_res.scalars().first()
    if not device:
        device = Device(
            device_id=claim.device_id,
            user_id=p_code.user_id,
            name=claim.device_name or p_code.device_name,
            platform=claim.platform,
            status=DeviceStatusEnum.ONLINE,
            health_score=100
        )
        db.add(device)
    else:
        device.user_id = p_code.user_id
        device.name = claim.device_name
        device.status = DeviceStatusEnum.ONLINE

    await db.commit()
    await db.refresh(device)
    
    return {
        "status": "paired",
        "device_id": device.device_id,
        "device_name": device.name,
        "api_key": f"sk_node_{secrets.token_hex(24)}"
    }
