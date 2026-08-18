import secrets
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.devices import Device, DeviceStatusEnum
from app.schemas.device import DeviceRegister, DeviceUpdate, DeviceResponse
from app.services.signaling_manager import signaling_manager

router = APIRouter(prefix="/devices", tags=["Devices"])

@router.get("", response_model=List[DeviceResponse])
async def list_devices(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Device).order_by(Device.created_at.desc()))
    devices = result.scalars().all()
    for dev in devices:
        dev.is_online = signaling_manager.is_node_online(dev.device_id)
    return devices

@router.get("/{device_id}", response_model=DeviceResponse)
async def get_device(device_id: str, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Device).where(Device.device_id == device_id))
    device = result.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    device.is_online = signaling_manager.is_node_online(device.device_id)
    return device

@router.post("/register", response_model=DeviceResponse)
async def register_device(device_in: DeviceRegister, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Device).where(Device.device_id == device_in.device_id))
    existing = result.scalars().first()
    if existing:
        existing.name = device_in.name
        existing.resolution = device_in.resolution or existing.resolution
        existing.target_fps = device_in.target_fps or existing.target_fps
        existing.target_bitrate_kbps = device_in.target_bitrate_kbps or existing.target_bitrate_kbps
        await db.commit()
        await db.refresh(existing)
        existing.is_online = signaling_manager.is_node_online(existing.device_id)
        existing.api_key = f"sk_node_{secrets.token_hex(16)}"
        return existing
        
    api_key = f"sk_node_{secrets.token_hex(24)}"
    new_device = Device(
        device_id=device_in.device_id,
        user_id=1,
        name=device_in.name,
        resolution=device_in.resolution or "1080p",
        target_fps=device_in.target_fps or 30,
        target_bitrate_kbps=device_in.target_bitrate_kbps or 1500,
        lens_facing=device_in.lens_facing or "BACK",
        torch_enabled=device_in.torch_enabled or False,
        recording_mode="MOTION",
        motion_sensitivity=device_in.motion_sensitivity or 50,
        status=DeviceStatusEnum.ONLINE,
        health_score=100
    )
    db.add(new_device)
    await db.commit()
    await db.refresh(new_device)
    new_device.is_online = True
    new_device.api_key = api_key
    return new_device

@router.patch("/{device_id}", response_model=DeviceResponse)
async def update_device(device_id: str, device_in: DeviceUpdate, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Device).where(Device.device_id == device_id))
    device = result.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
        
    update_data = device_in.model_dump(exclude_unset=True)
    for field, val in update_data.items():
        setattr(device, field, val)
        
    await db.commit()
    await db.refresh(device)
    device.is_online = signaling_manager.is_node_online(device.device_id)
    device.api_key = f"sk_node_{secrets.token_hex(16)}"
    return device

@router.delete("/{device_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_device(device_id: str, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Device).where(Device.device_id == device_id))
    device = result.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    await db.delete(device)
    await db.commit()
    return None
