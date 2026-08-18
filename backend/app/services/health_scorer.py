from app.models.devices import Device, DeviceStatus, DeviceStatusEnum

class HealthScorer:
    @staticmethod
    def calculate_health_score(status: DeviceStatus) -> tuple[int, DeviceStatusEnum]:
        if not status:
            return 50, DeviceStatusEnum.DEGRADED

        score = 0

        # 1. Battery health (25 points)
        bat = status.battery_level or 100
        if bat >= 50 or status.is_charging in ("AC", "USB"):
            score += 25
        elif bat >= 20:
            score += 15
        else:
            score += 5

        # 2. Thermal health (25 points)
        temp = status.temperature_c or 32.0
        if temp < 38.0:
            score += 25
        elif temp <= 42.0:
            score += 15
        elif temp <= 45.0:
            score += 5
        else:
            score += 0 # Thermal Critical!

        # 3. Storage availability (25 points)
        free_mb = status.storage_free_mb or 10000
        total_mb = status.storage_total_mb or 64000
        free_ratio = free_mb / max(total_mb, 1)
        if free_ratio >= 0.25:
            score += 25
        elif free_ratio >= 0.10:
            score += 15
        else:
            score += 0 # Storage warning!

        # 4. Network signal quality (25 points)
        rssi = status.wifi_rssi_dbm or -55
        if rssi >= -65:
            score += 25
        elif rssi >= -78:
            score += 18
        elif rssi >= -88:
            score += 10
        else:
            score += 5

        # Classification
        if temp > 45.0 or free_ratio < 0.05 or score < 60:
            status_enum = DeviceStatusEnum.DEGRADED
        else:
            status_enum = DeviceStatusEnum.ONLINE

        return score, status_enum

health_scorer = HealthScorer()
