import pytest
import base64
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.core.database import init_db, AsyncSessionLocal
from app.models.faces import FaceProfile, FaceEmbedding
from app.models.recordings import Recording
from app.models.devices import Device, DeviceStatusEnum
from app.models.users import User
from app.core.security import create_access_token
from sqlalchemy.future import select

@pytest.fixture(scope="session")
def anyio_backend():
    return "asyncio"

@pytest.mark.asyncio
async def test_security_headers_present():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        res = await ac.get("/api/v1/system/health")
        assert res.status_code == 200
        assert res.headers.get("X-Content-Type-Options") == "nosniff"
        assert res.headers.get("X-Frame-Options") == "DENY"
        assert res.headers.get("X-XSS-Protection") == "1; mode=block"
        assert "Strict-Transport-Security" in res.headers
        assert "Content-Security-Policy" in res.headers

@pytest.mark.asyncio
async def test_password_policy_enforcement():
    import uuid
    uid = uuid.uuid4().hex[:6]
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Weak password 1: Too short (<8 chars)
        res_short = await ac.post("/api/v1/auth/register", json={
            "username": f"shortpass_{uid}",
            "email": f"short_{uid}@test.local",
            "password": "p1"
        })
        assert res_short.status_code == 422

        # Weak password 2: No numbers
        res_nonum = await ac.post("/api/v1/auth/register", json={
            "username": f"nonum_{uid}",
            "email": f"nonum_{uid}@test.local",
            "password": "onlylettershere"
        })
        assert res_nonum.status_code == 422

        # Strong password: 8+ chars with letters & numbers
        res_strong = await ac.post("/api/v1/auth/register", json={
            "username": f"strong_{uid}",
            "email": f"strong_{uid}@test.local",
            "password": "SecurePassword123!"
        })
        assert res_strong.status_code == 200
        assert res_strong.json()["username"] == f"strong_{uid}"


@pytest.mark.asyncio
async def test_face_enrollment_consent_security_enforcement():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Attempt without explicit consent -> Must fail with 400
        unauthorized_face = {
            "display_name": "Intruder",
            "embedding_vector": [0.2] * 128,
            "consent_granted": False
        }
        res = await ac.post("/api/v1/faces/enroll", json=unauthorized_face)
        assert res.status_code == 400
        assert "consent is mandatory" in res.json()["detail"].lower()

@pytest.mark.asyncio
async def test_biometric_face_encryption_at_rest():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        enroll_payload = {
            "display_name": "Verified Resident",
            "embedding_vector": [0.35] * 128,
            "consent_granted": True
        }
        res = await ac.post("/api/v1/faces/enroll", json=enroll_payload)
        assert res.status_code == 200
        profile_id = res.json()["profile_id"]

    # Verify in DB that the stored ciphertext is Fernet AES encrypted (not plain base64 or plaintext)
    async with AsyncSessionLocal() as db:
        emb_res = await db.execute(select(FaceEmbedding).where(FaceEmbedding.profile_id == profile_id))
        embedding = emb_res.scalars().first()
        assert embedding is not None
        # Fernet tokens start with gAAAAA
        assert embedding.encrypted_embedding.startswith("gAAAAA")
        # Plaintext vector representation must NOT be visible
        assert "0.35" not in embedding.encrypted_embedding

@pytest.mark.asyncio
async def test_device_privacy_mode_security_isolation():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Toggle Privacy Mode to True
        res = await ac.post("/api/v1/faces/device/cam_int_test_01/privacy-mode?enable=true")
        assert res.status_code == 200
        assert res.json()["privacy_mode"] == True
        # Face recognition must be disabled when privacy mode is active
        assert res.json()["face_recognition_enabled"] == False

from datetime import datetime, timezone

@pytest.mark.asyncio
async def test_locked_recording_deletion_protection():
    async with AsyncSessionLocal() as db:
        dev_res = await db.execute(select(Device).limit(1))
        dev = dev_res.scalars().first()
        if not dev:
            dev = Device(
                device_id="cam_lock_test",
                user_id=1,
                name="Lock Test Cam",
                status=DeviceStatusEnum.ONLINE
            )
            db.add(dev)
            await db.commit()
            await db.refresh(dev)

        locked_rec = Recording(
            device_db_id=dev.id,
            start_time=datetime.now(timezone.utc),
            recording_mode="MOTION",
            file_path="/home/ubuntu/sentinelcam/backend/uploads/recordings/dummy_locked.mp4",
            is_locked=True,
            status="COMPLETED"
        )
        db.add(locked_rec)
        await db.commit()
        await db.refresh(locked_rec)
        rec_id = locked_rec.id

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Attempting to delete locked recording must fail with 403
        del_res = await ac.delete(f"/api/v1/recordings/{rec_id}")
        assert del_res.status_code == 403
        assert "locked evidence" in del_res.json()["detail"].lower()

@pytest.mark.asyncio
async def test_path_traversal_recording_rejection():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Attempt upload with path traversal device_id
        res = await ac.post("/api/v1/recordings/upload", data={
            "device_id": "../../etc/cron.d",
            "start_time": "2026-08-19T10:00:00Z",
            "duration_seconds": 10.0,
            "recording_mode": "MOTION"
        }, files={"video_file": ("test.mp4", b"fake_mp4_bytes", "video/mp4")})
        assert res.status_code == 400
        assert "invalid device_id" in res.json()["detail"].lower()

@pytest.mark.asyncio
async def test_deactivated_user_token_rejection():
    import uuid
    uid = uuid.uuid4().hex[:6]
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Register user
        reg_res = await ac.post("/api/v1/auth/register", json={
            "username": f"deact_{uid}",
            "email": f"deact_{uid}@test.local",
            "password": "SecurePassword123!"
        })
        assert reg_res.status_code == 200

    # Deactivate user in DB
    async with AsyncSessionLocal() as db:
        u_res = await db.execute(select(User).where(User.username == f"deact_{uid}"))
        user = u_res.scalars().first()
        user.is_active = False
        await db.commit()

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Login attempt must fail with 403
        login_res = await ac.post("/api/v1/auth/login", data={
            "username": f"deact_{uid}",
            "password": "SecurePassword123!"
        })
        assert login_res.status_code == 403
        assert "deactivated" in login_res.json()["detail"].lower()

        # Direct token attempt must also fail with 403
        token = create_access_token(subject=f"deact_{uid}")
        me_res = await ac.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
        assert me_res.status_code == 403
        assert "deactivated" in me_res.json()["detail"].lower()

@pytest.mark.asyncio
async def test_tripwire_coordinate_normalization_validation():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Tripwire with invalid unnormalized coordinates (> 1.0)
        invalid_tw = {
            "name": "Perimeter Line",
            "point_a_x": 1.5,
            "point_a_y": 0.2,
            "point_b_x": 0.5,
            "point_b_y": 0.5
        }
        res = await ac.post("/api/v1/devices/cam_livingroom_01/tripwires", json=invalid_tw)
        assert res.status_code == 422

@pytest.mark.asyncio
async def test_pairing_code_security_and_validation():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Generate pairing code
        gen_res = await ac.post("/api/v1/devices/pairing/generate")
        assert gen_res.status_code == 200
        code = gen_res.json()["pairing_code"]
        assert code.startswith("SENT-")
        assert len(code) == 11 # SENT- + 6 hex

        # Claim with invalid device_id format (special characters)
        claim_res = await ac.post("/api/v1/devices/pairing/claim", json={
            "pairing_code": code,
            "device_id": "../invalid/id",
            "device_name": "Living Room Cam"
        })
        assert claim_res.status_code == 422

