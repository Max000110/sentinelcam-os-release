import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.core.database import init_db

@pytest.fixture(scope="session")
def anyio_backend():
    return "asyncio"

@pytest.mark.asyncio
async def test_full_device_lifecycle_and_telemetry():
    await init_db()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # 1. Register device
        dev_payload = {
            "device_id": "cam_int_test_01",
            "name": "Integration Test Camera",
            "resolution": "1080p",
            "target_fps": 30,
            "target_bitrate_kbps": 2000
        }
        res_reg = await ac.post("/api/v1/devices/register", json=dev_payload)
        assert res_reg.status_code == 200
        assert res_reg.json()["device_id"] == "cam_int_test_01"

        # 2. Post Telemetry Heartbeat
        heartbeat_payload = {
            "device_id": "cam_int_test_01",
            "battery_level": 88,
            "is_charging": "AC",
            "temperature_c": 34.5,
            "storage_free_mb": 18500,
            "storage_total_mb": 64000,
            "network_type": "WIFI",
            "wifi_rssi_dbm": -52,
            "current_fps": 30.0,
            "current_bitrate_kbps": 1950.0
        }
        res_hb = await ac.post("/api/v1/telemetry/heartbeat", json=heartbeat_payload)
        assert res_hb.status_code == 200
        assert res_hb.json()["health_score"] >= 80

        # 3. Fetch latest telemetry
        res_tel = await ac.get("/api/v1/telemetry/cam_int_test_01/latest")
        assert res_tel.status_code == 200
        assert res_tel.json()["battery_level"] == 88
        assert res_tel.json()["temperature_c"] == 34.5

@pytest.mark.asyncio
async def test_ai_event_ingestion_and_zone_alert():
    await init_db()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # 1. Define Protected Zone for camera
        zone_payload = {
            "name": "Backyard Gate",
            "zone_type": "PROTECTED",
            "polygon": [[0.1, 0.1], [0.8, 0.1], [0.8, 0.8], [0.1, 0.8]]
        }
        await ac.post("/api/v1/devices/cam_int_test_01/zones", json=zone_payload)

        # 2. Ingest AI Detection of a person inside the zone
        ai_payload = {
            "device_id": "cam_int_test_01",
            "object_class": "person",
            "confidence": 0.95,
            "bbox_json": '{"x": 0.4, "y": 0.4, "w": 0.2, "h": 0.3}',
            "track_id": 101,
            "model_name": "yolov8n-tflite"
        }
        res_ai = await ac.post("/api/v1/ai/events", data=ai_payload)
        assert res_ai.status_code == 200
        assert res_ai.json()["status"] == "ok"
        assert "PERSON_ENTERED_PROTECTED_ZONE" in res_ai.json()["event_type"]

        # 3. Query AI events timeline
        res_events = await ac.get("/api/v1/ai/events?device_id=cam_int_test_01")
        assert res_events.status_code == 200
        assert len(res_events.json()) >= 1
