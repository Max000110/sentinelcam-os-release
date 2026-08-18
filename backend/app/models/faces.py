from datetime import datetime, timezone
from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey, Text
from sqlalchemy.orm import relationship
from app.core.database import Base

class FaceProfile(Base):
    __tablename__ = "face_profiles"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    device_db_id = Column(Integer, ForeignKey("devices.id"), nullable=True, index=True) # None = account-wide
    
    display_name = Column(String(100), nullable=False)
    status = Column(String(20), default="ACTIVE") # ACTIVE, DISABLED
    consent_granted = Column(Boolean, default=True) # Explicit opt-in consent
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc))

    embeddings = relationship("FaceEmbedding", back_populates="profile", cascade="all, delete-orphan")

class FaceEmbedding(Base):
    __tablename__ = "face_embeddings"

    id = Column(Integer, primary_key=True, index=True)
    profile_id = Column(Integer, ForeignKey("face_profiles.id"), nullable=False, index=True)
    
    embedding_version = Column(String(20), default="1.0")
    encrypted_embedding = Column(Text, nullable=False) # AES-256-GCM encrypted vector payload
    model_version = Column(String(50), default="mobilefacenet-v1")
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    revoked_at = Column(DateTime, nullable=True)

    profile = relationship("FaceProfile", back_populates="embeddings")
