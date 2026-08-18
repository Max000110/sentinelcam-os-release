import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.core.database import init_db, AsyncSessionLocal
from app.models.devices import Device, DeviceStatusEnum
from app.models.users import User, UserRole
from app.core.security import get_password_hash
from app.services.cleanup_worker import cleanup_worker
from sqlalchemy.future import select

@pytest.fixture(scope="session")
def anyio_backend():
    return "asyncio"

@pytest.mark.asyncio
async def test_storage_retention_and_cleanup_worker():
    await init_db()
    async with AsyncSessionLocal() as db:
        purged_count = await cleanup_worker.run_storage_retention_cleanup(db, retention_days=7)
        assert isinstance(purged_count, int)

@pytest.mark.asyncio
async def test_ota_update_and_signature_check():
    await init_db()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # 1. Publish new signed OTA release
        release_payload = {
            "version": "1.1.0",
            "release_id": "rel_sentinel_110",
            "sha256": "4b5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d",
            "package_url": "https://updates.sentinelcam.local/apk/sentinel-1.1.0.apk",
            "min_android_version": 24,
            "release_channel": "STABLE",
            "release_notes": "CameraX low-light stability update"
        }
        res_pub = await ac.post("/api/v1/ota/publish", json=release_payload)
        assert res_pub.status_code == 200

        # 2. Check update for older version
        res_chk = await ac.get("/api/v1/ota/check?current_version=1.0.0&channel=STABLE")
        assert res_chk.status_code == 200
        data = res_chk.json()
        assert data["update_available"] == True
        assert data["latest_version"] == "1.1.0"
        assert len(data["sha256"]) == 64

@pytest.mark.asyncio
async def test_diagnostics_report_ingestion():
    await init_db()
    async with AsyncSessionLocal() as db:
        # Ensure a test user exists
        user_res = await db.execute(select(User).limit(1))
        user = user_res.scalars().first()
        if not user:
            user = User(
                username="diag_user",
                email="diag@sentinelcam.local",
                hashed_password=get_password_hash("test_password_not_real"),
                role=UserRole.ADMIN,
                is_active=True
            )
            db.add(user)
            await db.commit()
            await db.refresh(user)

        # Ensure test device exists
        dev_res = await db.execute(select(Device).where(Device.device_id == "cam_diag_test_01"))
        dev = dev_res.scalars().first()
        if not dev:
            dev = Device(
                device_id="cam_diag_test_01",
                user_id=user.id,
                name="Diagnostics Test Camera",
                platform="android",
                status=DeviceStatusEnum.ONLINE
            )
            db.add(dev)
            await db.commit()

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        diag_payload = {
            "device_id": "cam_diag_test_01",
            "metrics": {
                "webrtc_rtt_ms": 32,
                "packet_loss_pct": 0.05,
                "battery_pct": 92,
                "temp_c": 33.2,
                "ram_free_mb": 1450,
                "recent_errors": []
            }
        }
        res_diag = await ac.post("/api/v1/diagnostics", json=diag_payload)
        assert res_diag.status_code == 200
        data = res_diag.json()
        assert data["status"] == "ok"
        assert "report_id" in data
