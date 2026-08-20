import requests
import uuid
import sys

BASE = "http://127.0.0.1:8000"

def test_security_headers():
    r = requests.get(f"{BASE}/api/v1/system/health")
    assert r.status_code == 200, f"Health failed: {r.status_code}"
    assert r.headers.get("X-Content-Type-Options") == "nosniff"
    assert r.headers.get("X-Frame-Options") == "DENY"
    assert r.headers.get("X-XSS-Protection") == "1; mode=block"
    assert "Strict-Transport-Security" in r.headers
    assert "Content-Security-Policy" in r.headers
    print("PASS: test_security_headers")

def test_password_policy():
    uid = uuid.uuid4().hex[:6]
    # Weak 1: Short
    r1 = requests.post(f"{BASE}/api/v1/auth/register", json={
        "username": f"short_{uid}", "email": f"short_{uid}@test.local", "password": "p1"
    })
    assert r1.status_code == 422, f"Short pass should be 422, got {r1.status_code}"

    # Weak 2: No numbers
    r2 = requests.post(f"{BASE}/api/v1/auth/register", json={
        "username": f"nonum_{uid}", "email": f"nonum_{uid}@test.local", "password": "onlyletters"
    })
    assert r2.status_code == 422, f"No-num pass should be 422, got {r2.status_code}"

    # Strong
    r3 = requests.post(f"{BASE}/api/v1/auth/register", json={
        "username": f"strong_{uid}", "email": f"strong_{uid}@test.local", "password": "SecurePassword123!"
    })
    assert r3.status_code == 200, f"Strong pass should be 200, got {r3.status_code}"
    print("PASS: test_password_policy")

def test_face_consent():
    r = requests.post(f"{BASE}/api/v1/faces/enroll", json={
        "display_name": "Intruder", "embedding_vector": [0.2] * 128, "consent_granted": False
    })
    assert r.status_code == 400, f"Consent required should be 400, got {r.status_code}"
    print("PASS: test_face_consent")

def test_device_privacy():
    r = requests.post(f"{BASE}/api/v1/faces/device/cam_livingroom_01/privacy-mode?enable=true")
    assert r.status_code == 200, f"Privacy mode should be 200, got {r.status_code}"
    assert r.json()["privacy_mode"] is True
    print("PASS: test_device_privacy")

def test_path_traversal_rejection():
    r = requests.post(f"{BASE}/api/v1/recordings/upload", data={
        "device_id": "../../etc/cron.d",
        "start_time": "2026-08-19T10:00:00Z",
        "duration_seconds": 10.0,
        "recording_mode": "MOTION"
    }, files={"video_file": ("test.mp4", b"fake_bytes", "video/mp4")})
    assert r.status_code == 400, f"Path traversal should be 400, got {r.status_code}"
    print("PASS: test_path_traversal_rejection")

def test_ice_servers():
    r = requests.get(f"{BASE}/api/v1/stream/ice-servers")
    assert r.status_code == 200, f"ICE servers should be 200, got {r.status_code}"
    data = r.json()
    assert "iceServers" in data
    print("PASS: test_ice_servers")

if __name__ == "__main__":
    test_security_headers()
    test_password_policy()
    test_face_consent()
    test_device_privacy()
    test_path_traversal_rejection()
    test_ice_servers()
    print("\nALL LIVE INTEGRATION & SECURITY TESTS PASSED (6/6)!")
