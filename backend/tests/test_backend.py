import pytest
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.core.database import init_db

@pytest.fixture(scope="session")
def anyio_backend():
    return "asyncio"

@pytest.mark.asyncio
async def test_system_root_and_health():
    await init_db()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        res = await ac.get("/")
        assert res.status_code == 200
        assert res.json()["system"] == "SentinelCam"
        
        health_res = await ac.get("/api/v1/system/health")
        assert health_res.status_code == 200
        assert health_res.json()["status"] == "HEALTHY"

@pytest.mark.asyncio
async def test_fleet_overview():
    await init_db()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        res = await ac.get("/api/v1/fleet/overview")
        assert res.status_code == 200
        data = res.json()
        assert "total_devices" in data
        assert "average_health_score" in data

@pytest.mark.asyncio
async def test_zones_crud():
    await init_db()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Register device for test
        await ac.post("/api/v1/devices/register", json={
            "device_id": "test_cam_zone_01",
            "name": "Zone Test Cam"
        })
        
        # Create Polygonal Zone
        zone_payload = {
            "name": "Driveway Protected Zone",
            "zone_type": "PROTECTED",
            "polygon": [[0.1, 0.1], [0.5, 0.1], [0.5, 0.8], [0.1, 0.8]]
        }
        res = await ac.post("/api/v1/devices/test_cam_zone_01/zones", json=zone_payload)
        assert res.status_code == 200
        assert res.json()["name"] == "Driveway Protected Zone"

        # List Zones
        list_res = await ac.get("/api/v1/devices/test_cam_zone_01/zones")
        assert list_res.status_code == 200
        assert len(list_res.json()) >= 1

@pytest.mark.asyncio
async def test_face_enrollment_consent_validation():
    await init_db()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Attempt enrollment without consent -> Must fail with 400
        bad_payload = {
            "display_name": "Unknown Person",
            "embedding_vector": [0.1] * 128,
            "consent_granted": False
        }
        res_fail = await ac.post("/api/v1/faces/enroll", json=bad_payload)
        assert res_fail.status_code == 400

        # Attempt enrollment with explicit opt-in consent -> Success
        good_payload = {
            "display_name": "Home Owner",
            "embedding_vector": [0.1] * 128,
            "consent_granted": True
        }
        res_ok = await ac.post("/api/v1/faces/enroll", json=good_payload)
        assert res_ok.status_code == 200
        assert res_ok.json()["status"] == "enrolled"

@pytest.mark.asyncio
async def test_pairing_code_flow():
    await init_db()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # Generate code
        gen_res = await ac.post("/api/v1/devices/pairing/generate?device_name=Garage+Phone")
        assert gen_res.status_code == 200
        p_code = gen_res.json()["pairing_code"]
        assert p_code.startswith("SENT-")

        # Claim code
        claim_res = await ac.post("/api/v1/devices/pairing/claim", json={
            "pairing_code": p_code,
            "device_id": "test_cam_claimed_99",
            "device_name": "Garage Phone"
        })
        assert claim_res.status_code == 200
        assert claim_res.json()["status"] == "paired"
