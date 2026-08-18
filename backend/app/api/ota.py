from datetime import datetime, timezone
from typing import Optional
from pydantic import BaseModel
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.fleet_and_ota import OtaRelease

router = APIRouter(prefix="/ota", tags=["OTA Updates & Rollbacks"])

class OtaPublishRequest(BaseModel):
    version: str
    release_id: str
    sha256: str
    package_url: str
    min_android_version: int = 24
    release_channel: str = "STABLE" # STABLE, BETA, CANARY
    release_notes: Optional[str] = None

@router.get("/check")
async def check_for_updates(
    current_version: str,
    channel: str = "STABLE",
    db: AsyncSession = Depends(get_db)
):
    result = await db.execute(
        select(OtaRelease)
        .where(OtaRelease.status == "ACTIVE", OtaRelease.release_channel == channel)
        .order_by(OtaRelease.created_at.desc())
        .limit(1)
    )
    latest = result.scalars().first()
    if not latest or latest.version == current_version:
        return {"update_available": False, "current_version": current_version}

    return {
        "update_available": True,
        "latest_version": latest.version,
        "release_id": latest.release_id,
        "sha256": latest.sha256,
        "package_url": latest.package_url,
        "release_notes": latest.release_notes,
        "min_android_version": latest.min_android_version
    }

@router.post("/publish")
async def publish_ota_release(payload: OtaPublishRequest, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(OtaRelease).where(OtaRelease.version == payload.version))
    existing = res.scalars().first()

    if existing:
        existing.release_id = payload.release_id
        existing.sha256 = payload.sha256
        existing.package_url = payload.package_url
        existing.min_android_version = payload.min_android_version
        existing.release_channel = payload.release_channel
        existing.release_notes = payload.release_notes
        existing.status = "ACTIVE"
        existing.created_at = datetime.now(timezone.utc)
        release = existing
    else:
        release = OtaRelease(
            version=payload.version,
            release_id=payload.release_id,
            sha256=payload.sha256,
            package_url=payload.package_url,
            min_android_version=payload.min_android_version,
            release_channel=payload.release_channel,
            release_notes=payload.release_notes,
            status="ACTIVE"
        )
        db.add(release)

    await db.commit()
    await db.refresh(release)
    return {"status": "published", "version": release.version, "release_id": release.release_id}

@router.post("/rollback")
async def rollback_ota_release(version: str, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(OtaRelease).where(OtaRelease.version == version))
    release = res.scalars().first()
    if not release:
        raise HTTPException(status_code=404, detail="Release not found")

    release.status = "REVOKED"
    await db.commit()
    return {"status": "revoked", "version": version}
