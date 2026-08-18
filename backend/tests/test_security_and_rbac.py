import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.core.database import init_db

@pytest.fixture(scope="session")
def anyio_backend():
    return "asyncio"

@pytest.mark.asyncio
async def test_face_enrollment_consent_security_enforcement():
    await init_db()
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
async def test_device_privacy_mode_security_isolation():
    await init_db()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Toggle Privacy Mode to True
        res = await ac.post("/api/v1/faces/device/cam_int_test_01/privacy-mode?enable=true")
        assert res.status_code == 200
        assert res.json()["privacy_mode"] == True
        # Face recognition must be disabled when privacy mode is active
        assert res.json()["face_recognition_enabled"] == False
