import re
import secrets
from datetime import datetime, timedelta, timezone
from typing import List, Optional
from pydantic import BaseModel, field_validator
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.incidents_and_audit import Incident
from app.models.devices import PairingCode, Device, DeviceStatusEnum
from app.models.users import User

router = APIRouter(tags=["Incidents & Device Pairing"])

class PairingClaimRequest(BaseModel):
    pairing_code: str
    device_id: str
    device_name: str
    platform: str = "android"

    @field_validator("device_id")
    @classmethod
    def validate_device_id(cls, v: str) -> str:
        if not re.match(r"^[a-zA-Z0-9_-]{3,64}$", v):
            raise ValueError("device_id must be 3-64 characters containing alphanumeric, dashes, or underscores")
        return v

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
    # Generate high-entropy single-use pairing code e.g. "SENT-8492AF"
    code_str = f"SENT-{secrets.token_hex(3).upper()}"
    user_res = await db.execute(select(User.id).order_by(User.id.asc()).limit(1))
    owner_user_id = user_res.scalars().first() or 1

    p_code = PairingCode(
        user_id=owner_user_id,
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
