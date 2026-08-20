import json
from typing import List, Optional
from pydantic import BaseModel, field_validator
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.devices import Device
from app.models.zones_and_rules import Zone, Tripwire, ZoneType, TripwireDirection

router = APIRouter(tags=["Detection Zones & Tripwires"])

class ZoneCreate(BaseModel):
    name: str
    zone_type: ZoneType = ZoneType.PROTECTED
    polygon: List[List[float]] # e.g. [[0.1, 0.2], [0.4, 0.2], [0.4, 0.6], [0.1, 0.6]]

class TripwireCreate(BaseModel):
    name: str
    point_a_x: float
    point_a_y: float
    point_b_x: float
    point_b_y: float
    direction: TripwireDirection = TripwireDirection.ANY

    @field_validator("point_a_x", "point_a_y", "point_b_x", "point_b_y")
    @classmethod
    def validate_normalized_coord(cls, v: float) -> float:
        if not (0.0 <= v <= 1.0):
            raise ValueError("Tripwire coordinates must be normalized between 0.0 and 1.0")
        return v

@router.get("/devices/{device_id}/zones")
async def get_device_zones(device_id: str, db: AsyncSession = Depends(get_db)):
    dev_res = await db.execute(select(Device).where(Device.device_id == device_id))
    device = dev_res.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
        
    zones_res = await db.execute(select(Zone).where(Zone.device_db_id == device.id))
    zones = zones_res.scalars().all()
    return [
        {
            "id": z.id,
            "name": z.name,
            "zone_type": z.zone_type.value,
            "polygon": json.loads(z.polygon_json),
            "is_active": z.is_active
        }
        for z in zones
    ]

@router.post("/devices/{device_id}/zones")
async def create_zone(device_id: str, zone_in: ZoneCreate, db: AsyncSession = Depends(get_db)):
    dev_res = await db.execute(select(Device).where(Device.device_id == device_id))
    device = dev_res.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    if len(zone_in.polygon) < 3:
        raise HTTPException(status_code=400, detail="Polygon must contain at least 3 coordinate points")

    for pt in zone_in.polygon:
        if len(pt) != 2 or not (0.0 <= pt[0] <= 1.0) or not (0.0 <= pt[1] <= 1.0):
            raise HTTPException(status_code=400, detail="Coordinates must be normalized between 0.0 and 1.0")

    zone = Zone(
        device_db_id=device.id,
        name=zone_in.name,
        zone_type=zone_in.zone_type,
        polygon_json=json.dumps(zone_in.polygon),
        is_active=True
    )
    db.add(zone)
    await db.commit()
    await db.refresh(zone)
    return {
        "id": zone.id,
        "name": zone.name,
        "zone_type": zone.zone_type.value,
        "polygon": zone_in.polygon
    }

@router.delete("/zones/{zone_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_zone(zone_id: int, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(Zone).where(Zone.id == zone_id))
    zone = res.scalars().first()
    if not zone:
        raise HTTPException(status_code=404, detail="Zone not found")
    await db.delete(zone)
    await db.commit()
    return None

@router.get("/devices/{device_id}/tripwires")
async def get_device_tripwires(device_id: str, db: AsyncSession = Depends(get_db)):
    dev_res = await db.execute(select(Device).where(Device.device_id == device_id))
    device = dev_res.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
        
    tw_res = await db.execute(select(Tripwire).where(Tripwire.device_db_id == device.id))
    tripwires = tw_res.scalars().all()
    return tripwires

@router.post("/devices/{device_id}/tripwires")
async def create_tripwire(device_id: str, tw_in: TripwireCreate, db: AsyncSession = Depends(get_db)):
    dev_res = await db.execute(select(Device).where(Device.device_id == device_id))
    device = dev_res.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    tw = Tripwire(
        device_db_id=device.id,
        name=tw_in.name,
        point_a_x=tw_in.point_a_x,
        point_a_y=tw_in.point_a_y,
        point_b_x=tw_in.point_b_x,
        point_b_y=tw_in.point_b_y,
        direction=tw_in.direction,
        is_active=True
    )
    db.add(tw)
    await db.commit()
    await db.refresh(tw)
    return tw
