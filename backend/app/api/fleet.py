from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy import func
from app.core.database import get_db
from app.models.devices import Device, DeviceGroup, DeviceStatus, DeviceStatusEnum
from app.models.incidents_and_audit import Incident
from app.models.recordings import Recording
from app.models.events import AiEvent
from app.services.signaling_manager import signaling_manager

router = APIRouter(prefix="/fleet", tags=["Fleet & Operations"])

@router.get("/overview")
async def get_fleet_overview(db: AsyncSession = Depends(get_db)):
    # Query all devices
    dev_res = await db.execute(select(Device))
    devices = dev_res.scalars().all()
    
    total_devices = len(devices)
    online_count = sum(1 for d in devices if signaling_manager.is_node_online(d.device_id))
    offline_count = total_devices - online_count
    
    # Calculate fleet average health score
    avg_health = sum(d.health_score for d in devices) // max(total_devices, 1)
    
    # Query open incidents
    inc_res = await db.execute(select(func.count(Incident.id)).where(Incident.status == "OPEN"))
    open_incidents = inc_res.scalar() or 0
    
    # Query total storage used
    rec_res = await db.execute(select(func.sum(Recording.file_size)).where(Recording.deleted_at == None))
    total_storage_bytes = rec_res.scalar() or 0
    total_storage_mb = round(total_storage_bytes / (1024 * 1024), 1)

    return {
        "total_devices": total_devices,
        "online_devices": online_count,
        "offline_devices": offline_count,
        "average_health_score": avg_health,
        "open_incidents": open_incidents,
        "total_storage_mb": total_storage_mb,
        "storage_quota_limit_mb": 50000,
        "storage_usage_pct": round((total_storage_mb / 50000) * 100, 1)
    }

@router.get("/groups")
async def list_groups(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DeviceGroup))
    groups = result.scalars().all()
    return groups

@router.post("/groups")
async def create_group(name: str, description: Optional[str] = None, db: AsyncSession = Depends(get_db)):
    group = DeviceGroup(user_id=1, name=name, description=description)
    db.add(group)
    await db.commit()
    await db.refresh(group)
    return group
