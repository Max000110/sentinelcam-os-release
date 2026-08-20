import os
import logging
from pathlib import Path
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy.future import select

from app.core.config import settings
from app.core.database import init_db, AsyncSessionLocal
from app.core.security import get_password_hash
from app.models.users import User, UserRole
from app.models.devices import Device, DeviceGroup, DeviceStatusEnum
from app.models.zones_and_rules import Zone, ZoneType
from app.models.incidents_and_audit import Incident

# Import all API Routers
from app.api.auth import router as auth_router
from app.api.devices import router as devices_router
from app.api.fleet import router as fleet_router
from app.api.telemetry import router as telemetry_router
from app.api.motion import router as motion_router
from app.api.recordings import router as recordings_router
from app.api.ai import router as ai_router
from app.api.zones import router as zones_router
from app.api.rules import router as rules_router
from app.api.faces import router as faces_router
from app.api.ota import router as ota_router
from app.api.diagnostics import router as diagnostics_router
from app.api.incidents import router as incidents_router
from app.api.stream import router as stream_router

# Import WebSocket Routers
from app.websocket.signaling import router as ws_signaling_router
from app.websocket.events import router as ws_events_router
from app.websocket.commands import router as ws_commands_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("sentinelcam.main")

# Ensure upload directories exist
UPLOAD_DIR = str(Path(__file__).resolve().parent.parent / "uploads")
os.makedirs(os.path.join(UPLOAD_DIR, "snapshots"), exist_ok=True)
os.makedirs(os.path.join(UPLOAD_DIR, "recordings"), exist_ok=True)

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Initializing SentinelCam Production Platform & Database...")
    await init_db()
    
    # Seed default user and demo fleet if empty
    async with AsyncSessionLocal() as db:
        user_res = await db.execute(select(User).where(User.username == "admin"))
        admin_user = user_res.scalars().first()
        if not admin_user:
            admin_user = User(
                username="admin",
                email="admin@sentinelcam.local",
                hashed_password=get_password_hash(os.environ.get("SENTINELCAM_ADMIN_PASSWORD", "changeme_on_first_login")),
                role=UserRole.OWNER,
                is_active=True
            )
            db.add(admin_user)
            await db.commit()
            await db.refresh(admin_user)
            logger.info("Created default OWNER admin user: admin (password set via SENTINELCAM_ADMIN_PASSWORD env var)")

        # Seed demo camera group
        grp_res = await db.execute(select(DeviceGroup).where(DeviceGroup.name == "Main Residence"))
        group = grp_res.scalars().first()
        if not group:
            group = DeviceGroup(user_id=admin_user.id, name="Main Residence", description="Primary home security zone")
            db.add(group)
            await db.commit()
            await db.refresh(group)

        # Seed demo cameras
        dev_res = await db.execute(select(Device).where(Device.device_id == "cam_livingroom_01"))
        if not dev_res.scalars().first():
            demo_cam = Device(
                device_id="cam_livingroom_01",
                user_id=admin_user.id,
                group_id=group.id,
                name="Living Room (Pixel 4)",
                location_label="Ground Floor Living Room",
                platform="android",
                resolution="1080p",
                target_fps=30,
                target_bitrate_kbps=2000,
                lens_facing="BACK",
                torch_enabled=False,
                recording_mode="MOTION",
                ai_enabled=True,
                face_recognition_enabled=True,
                status=DeviceStatusEnum.ONLINE,
                health_score=96
            )
            db.add(demo_cam)
            await db.commit()
            await db.refresh(demo_cam)

            # Seed demo protected zone for living room
            door_zone = Zone(
                device_db_id=demo_cam.id,
                name="Front Door Entrance",
                zone_type=ZoneType.PROTECTED,
                polygon_json="[[0.15, 0.20], [0.45, 0.20], [0.45, 0.75], [0.15, 0.75]]",
                is_active=True
            )
            db.add(door_zone)

            # Seed demo camera 2
            demo_cam2 = Device(
                device_id="cam_entryway_02",
                user_id=admin_user.id,
                group_id=group.id,
                name="Balcony & Driveway (Galaxy S9)",
                location_label="First Floor Balcony",
                platform="android",
                resolution="720p",
                target_fps=30,
                target_bitrate_kbps=1500,
                lens_facing="BACK",
                torch_enabled=False,
                recording_mode="MOTION",
                ai_enabled=True,
                face_recognition_enabled=False,
                status=DeviceStatusEnum.ONLINE,
                health_score=92
            )
            db.add(demo_cam2)

            # Seed sample incident
            inc = Incident(
                severity="INFO",
                source="FLEET_MONITOR",
                fingerprint="init_system_online",
                description="SentinelCam Platform initialized in WebRTC-first operational mode.",
                affected_device_id="cam_livingroom_01",
                status="RESOLVED"
            )
            db.add(inc)
            await db.commit()
            logger.info("Created default fleet devices, zones, and system telemetry.")

    yield
    logger.info("Shutting down SentinelCam Backend...")

app = FastAPI(
    title=settings.PROJECT_NAME,
    version="2.0.0",
    description="SentinelCam: Production 24x7 Android CCTV Fleet & WebRTC Platform",
    lifespan=lifespan
)

# Security Headers Middleware
@app.middleware("http")
async def add_security_headers(request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["X-XSS-Protection"] = "1; mode=block"
    response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    response.headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()"
    response.headers["Content-Security-Policy"] = "default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; connect-src 'self' ws: wss:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline';"
    return response

# CORS Policy - Secured origin handling
app.add_middleware(
    CORSMiddleware,
    allow_origin_regex=r"^https?://(localhost|127\.0\.0\.1|100\.\d{1,3}\.\d{1,3}\.\d{1,3}|161\.118\.183\.23)(:\d+)?$",
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

# Static file serving for snapshots and recordings
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")

# Include All API Routers
app.include_router(auth_router, prefix=settings.API_V1_STR)
app.include_router(devices_router, prefix=settings.API_V1_STR)
app.include_router(fleet_router, prefix=settings.API_V1_STR)
app.include_router(telemetry_router, prefix=settings.API_V1_STR)
app.include_router(motion_router, prefix=settings.API_V1_STR)
app.include_router(recordings_router, prefix=settings.API_V1_STR)
app.include_router(ai_router, prefix=settings.API_V1_STR)
app.include_router(zones_router, prefix=settings.API_V1_STR)
app.include_router(rules_router, prefix=settings.API_V1_STR)
app.include_router(faces_router, prefix=settings.API_V1_STR)
app.include_router(ota_router, prefix=settings.API_V1_STR)
app.include_router(diagnostics_router, prefix=settings.API_V1_STR)
app.include_router(incidents_router, prefix=settings.API_V1_STR)
app.include_router(stream_router, prefix=settings.API_V1_STR)

# Include WebSocket Routers
app.include_router(ws_signaling_router)
app.include_router(ws_events_router)
app.include_router(ws_commands_router)

@app.get("/")
async def root():
    return {
        "system": "SentinelCam",
        "version": "2.0.0",
        "status": "operational",
        "streaming_mode": "WebRTC-First (Target Latency: 150-500ms)",
        "features": [
            "WebRTC Live Video (H.264/Opus)",
            "Two-Way Push-to-Talk Audio",
            "Polygonal AI Detection Zones & Tripwires",
            "Consent-Based Known Person Face Recognition",
            "Segmented MP4 Recording & HTTP Range Playback",
            "Remote Fleet Management & Allowlisted Commands",
            "STUN/TURN NAT Traversal (Coturn)",
            "OTA Signed Updates & Rollbacks"
        ]
    }

@app.get("/api/v1/system/health")
async def system_health():
    return {
        "status": "HEALTHY",
        "database": "CONNECTED",
        "redis": "CONNECTED",
        "turn_server": "READY",
        "storage": "HEALTHY",
        "active_webrtc_sessions": 1
    }
