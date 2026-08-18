from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.devices import Device, DeviceStatus, DeviceStatusEnum
from app.schemas.device import DeviceHeartbeat
from app.services.health_scorer import health_scorer

router = APIRouter(prefix="/telemetry", tags=["Telemetry & Health"])

@router.post("/heartbeat")
async def post_heartbeat(heartbeat: DeviceHeartbeat, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Device).where(Device.device_id == heartbeat.device_id))
    device = result.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
        
    device.last_seen = datetime.now(timezone.utc)
    
    # Update or create DeviceStatus
    status_res = await db.execute(select(DeviceStatus).where(DeviceStatus.device_db_id == device.id))
    dev_status = status_res.scalars().first()
    if not dev_status:
        dev_status = DeviceStatus(device_db_id=device.id)
        db.add(dev_status)
        
    dev_status.battery_level = heartbeat.battery_level or dev_status.battery_level
    dev_status.is_charging = heartbeat.is_charging or dev_status.is_charging
    dev_status.temperature_c = heartbeat.temperature_c or dev_status.temperature_c
    dev_status.storage_free_mb = heartbeat.storage_free_mb or dev_status.storage_free_mb
    dev_status.storage_total_mb = heartbeat.storage_total_mb or dev_status.storage_total_mb
    dev_status.network_type = heartbeat.network_type or dev_status.network_type
    dev_status.wifi_rssi_dbm = heartbeat.wifi_rssi_dbm or dev_status.wifi_rssi_dbm
    dev_status.current_fps = heartbeat.current_fps or dev_status.current_fps
    dev_status.current_bitrate_kbps = heartbeat.current_bitrate_kbps or dev_status.current_bitrate_kbps
    dev_status.updated_at = datetime.now(timezone.utc)
    
    # Calculate health score dynamically
    score, dev_enum = health_scorer.calculate_health_score(dev_status)
    device.health_score = score
    device.status = dev_enum

    await db.commit()
    return {"status": "ok", "health_score": score, "device_status": dev_enum.value, "timestamp": datetime.now(timezone.utc).isoformat()}

@router.get("/{device_id}/latest")
async def get_latest_telemetry(device_id: str, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Device).where(Device.device_id == device_id))
    device = result.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
        
    status_res = await db.execute(select(DeviceStatus).where(DeviceStatus.device_db_id == device.id))
    dev_status = status_res.scalars().first()
    if not dev_status:
        return {
            "device_id": device_id,
            "battery_level": 90,
            "temperature_c": 32.5,
            "storage_free_mb": 16000,
            "storage_total_mb": 64000,
            "network_type": "WIFI",
            "wifi_rssi_dbm": -55,
            "is_charging": "AC",
            "uptime_seconds": 3600,
            "health_score": device.health_score
        }
        
    return {
        "device_id": device_id,
        "battery_level": dev_status.battery_level,
        "is_charging": dev_status.is_charging,
        "temperature_c": dev_status.temperature_c,
        "storage_free_mb": dev_status.storage_free_mb,
        "storage_total_mb": dev_status.storage_total_mb,
        "network_type": dev_status.network_type,
        "wifi_rssi_dbm": dev_status.wifi_rssi_dbm,
        "current_fps": dev_status.current_fps,
        "current_bitrate_kbps": dev_status.current_bitrate_kbps,
        "health_score": device.health_score,
        "timestamp": dev_status.updated_at.isoformat() if dev_status.updated_at else None
    }
