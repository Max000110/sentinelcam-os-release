import base64
import hashlib
from typing import List, Optional
from cryptography.fernet import Fernet
from pydantic import BaseModel, field_validator
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.config import settings
from app.core.database import get_db
from app.models.devices import Device
from app.models.faces import FaceProfile, FaceEmbedding

router = APIRouter(prefix="/faces", tags=["Known People & Face Intelligence"])

def _get_biometric_cipher() -> Fernet:
    key = base64.urlsafe_b64encode(hashlib.sha256(settings.JWT_SECRET_KEY.encode("utf-8")).digest())
    return Fernet(key)

class FaceEnrollRequest(BaseModel):
    display_name: str
    device_id: Optional[str] = None
    embedding_vector: List[float] # Normalized 128-dim or 512-dim face vector
    consent_granted: bool = True

    @field_validator("display_name")
    @classmethod
    def validate_name(cls, v: str) -> str:
        v = v.strip()
        if len(v) < 2 or len(v) > 100:
            raise ValueError("display_name must be between 2 and 100 characters")
        return v

    @field_validator("embedding_vector")
    @classmethod
    def validate_vector(cls, v: List[float]) -> List[float]:
        if len(v) not in (128, 512):
            raise ValueError("embedding_vector must have exactly 128 or 512 float dimensions")
        return v

@router.get("/profiles")
async def list_face_profiles(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(FaceProfile))
    profiles = result.scalars().all()
    return [
        {
            "id": p.id,
            "display_name": p.display_name,
            "status": p.status,
            "consent_granted": p.consent_granted,
            "created_at": p.created_at.isoformat() if p.created_at else None
        }
        for p in profiles
    ]

@router.post("/enroll")
async def enroll_face_profile(payload: FaceEnrollRequest, db: AsyncSession = Depends(get_db)):
    if not payload.consent_granted:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Explicit user consent is mandatory for Known-Person recognition profile enrollment."
        )

    # Cryptographically encrypt the biometric embedding vector with AES-GCM/Fernet
    vector_bytes = str(payload.embedding_vector).encode("utf-8")
    cipher = _get_biometric_cipher()
    encrypted_payload = cipher.encrypt(vector_bytes).decode("utf-8")

    profile = FaceProfile(
        user_id=1,
        display_name=payload.display_name,
        status="ACTIVE",
        consent_granted=True
    )
    db.add(profile)
    await db.commit()
    await db.refresh(profile)

    embedding = FaceEmbedding(
        profile_id=profile.id,
        embedding_version="1.0",
        encrypted_embedding=encrypted_payload,
        model_version="mobilefacenet-v1.2"
    )
    db.add(embedding)
    await db.commit()

    return {
        "status": "enrolled",
        "profile_id": profile.id,
        "display_name": profile.display_name,
        "model_version": "mobilefacenet-v1.2"
    }

@router.delete("/{profile_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_face_profile(profile_id: int, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(FaceProfile).where(FaceProfile.id == profile_id))
    profile = res.scalars().first()
    if not profile:
        raise HTTPException(status_code=404, detail="Face profile not found")
    await db.delete(profile)
    await db.commit()
    return None

@router.post("/device/{device_id}/privacy-mode")
async def toggle_device_privacy_mode(device_id: str, enable: bool, db: AsyncSession = Depends(get_db)):
    dev_res = await db.execute(select(Device).where(Device.device_id == device_id))
    device = dev_res.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
        
    device.privacy_mode = enable
    if enable:
        # Privacy mode disables face recognition and AI tracking immediately
        device.face_recognition_enabled = False
    await db.commit()
    return {
        "device_id": device.device_id,
        "privacy_mode": device.privacy_mode,
        "face_recognition_enabled": device.face_recognition_enabled
    }
