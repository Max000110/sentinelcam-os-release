import pytest
import hashlib
from app.core.security import get_password_hash, verify_password, create_access_token, decode_access_token
from app.services.ai_rule_engine import ai_rule_engine
from app.services.health_scorer import health_scorer
from app.models.devices import DeviceStatus, DeviceStatusEnum
from app.models.zones_and_rules import Tripwire, TripwireDirection

def test_security_hashing_and_tokens():
    raw_password = "SecretPassword123!"
    hashed = get_password_hash(raw_password)
    assert verify_password(raw_password, hashed)
    assert not verify_password("WrongPassword", hashed)

    token = create_access_token(subject="testuser")
    payload = decode_access_token(token)
    assert payload is not None
    assert payload["sub"] == "testuser"

def test_polygon_raycasting_algorithm():
    # Square zone: (0.2, 0.2) to (0.6, 0.6)
    polygon = [[0.2, 0.2], [0.6, 0.2], [0.6, 0.6], [0.2, 0.6]]

    # Point inside
    assert ai_rule_engine.is_point_in_polygon(0.4, 0.4, polygon) == True
    # Point outside
    assert ai_rule_engine.is_point_in_polygon(0.1, 0.1, polygon) == False
    assert ai_rule_engine.is_point_in_polygon(0.8, 0.8, polygon) == False

def test_tripwire_line_crossing():
    tw = Tripwire(
        name="Entry Gate",
        point_a_x=0.0, point_a_y=0.5,
        point_b_x=1.0, point_b_y=0.5,
        direction=TripwireDirection.ANY
    )
    # Moving downwards across y=0.5
    crossed = ai_rule_engine.check_tripwire_crossing(0.5, 0.2, 0.5, 0.8, tw)
    assert crossed == True

    # Moving parallel above y=0.5 without crossing
    not_crossed = ai_rule_engine.check_tripwire_crossing(0.1, 0.2, 0.9, 0.2, tw)
    assert not_crossed == False

def test_health_scorer_states():
    # Healthy status
    status_good = DeviceStatus(
        battery_level=95,
        is_charging="AC",
        temperature_c=32.0,
        storage_free_mb=20000,
        storage_total_mb=64000,
        wifi_rssi_dbm=-55
    )
    score_good, enum_good = health_scorer.calculate_health_score(status_good)
    assert score_good >= 90
    assert enum_good == DeviceStatusEnum.ONLINE

    # Critical thermal & storage status
    status_bad = DeviceStatus(
        battery_level=10,
        is_charging="NO",
        temperature_c=48.0, # High temperature!
        storage_free_mb=500, # Low storage!
        storage_total_mb=64000,
        wifi_rssi_dbm=-95
    )
    score_bad, enum_bad = health_scorer.calculate_health_score(status_bad)
    assert score_bad < 60
    assert enum_bad == DeviceStatusEnum.DEGRADED

def test_sha256_checksum_calculation():
    sample_data = b"SentinelCam Video Segment Frame Chunk"
    expected_hash = hashlib.sha256(sample_data).hexdigest()
    assert len(expected_hash) == 64
