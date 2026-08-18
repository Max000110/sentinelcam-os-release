import json
import logging
from datetime import datetime, timezone
from typing import List, Dict, Any, Optional
from app.models.events import AiEvent, EventSeverity
from app.models.zones_and_rules import Zone, Tripwire, Rule, ZoneType, TripwireDirection

logger = logging.getLogger("sentinelcam.ai_engine")

class AiRuleEngine:
    @staticmethod
    def is_point_in_polygon(x: float, y: float, polygon: List[List[float]]) -> bool:
        """Ray-casting algorithm to test if (x, y) is inside polygon."""
        num_vertices = len(polygon)
        if num_vertices < 3:
            return False
            
        inside = False
        p1x, p1y = polygon[0]
        for i in range(num_vertices + 1):
            p2x, p2y = polygon[i % num_vertices]
            if y > min(p1y, p2y):
                if y <= max(p1y, p2y):
                    if x <= max(p1x, p2x):
                        if p1y != p2y:
                            xinters = (y - p1y) * (p2x - p1x) / (p2y - p1y) + p1x
                        if p1x == p2x or x <= xinters:
                            inside = not inside
            p1x, p1y = p2x, p2y
        return inside

    @staticmethod
    def check_tripwire_crossing(
        prev_x: float, prev_y: float,
        curr_x: float, curr_y: float,
        tripwire: Tripwire
    ) -> bool:
        """Determines if a moving track line (prev -> curr) intersects tripwire segment (A -> B)."""
        ax, ay = tripwire.point_a_x, tripwire.point_a_y
        bx, by = tripwire.point_b_x, tripwire.point_b_y
        
        # Simple cross-product intersection check
        def ccw(x1, y1, x2, y2, x3, y3):
            return (y3 - y1) * (x2 - x1) > (y2 - y1) * (x3 - x1)
            
        intersects = (ccw(ax, ay, curr_x, curr_y, prev_x, prev_y) != ccw(bx, by, curr_x, curr_y, prev_x, prev_y)) and \
                     (ccw(ax, ay, bx, by, prev_x, prev_y) != ccw(ax, ay, bx, by, curr_x, curr_y))
        return intersects

    def evaluate_detection(
        self,
        device_db_id: int,
        object_class: str,
        confidence: float,
        bbox: Dict[str, float], # {x, y, w, h} normalized 0.0 - 1.0
        track_id: Optional[int],
        zones: List[Zone],
        rules: List[Rule],
        is_quiet_hours: bool = False
    ) -> Dict[str, Any]:
        center_x = bbox.get("x", 0.5) + (bbox.get("w", 0.0) / 2.0)
        center_y = bbox.get("y", 0.5) + (bbox.get("h", 0.0) / 2.0)

        matched_zone: Optional[Zone] = None
        for zone in zones:
            if not zone.is_active:
                continue
            try:
                poly = json.loads(zone.polygon_json)
                if self.is_point_in_polygon(center_x, center_y, poly):
                    matched_zone = zone
                    break
            except Exception as e:
                logger.error(f"Error evaluating zone polygon {zone.id}: {e}")

        # Default severity calculation
        severity = EventSeverity.LOW
        event_type = f"{object_class.upper()}_DETECTED"

        if matched_zone:
            if matched_zone.zone_type == ZoneType.PROTECTED:
                severity = EventSeverity.HIGH if is_quiet_hours else EventSeverity.MEDIUM
                event_type = f"{object_class.upper()}_ENTERED_PROTECTED_ZONE"
            elif matched_zone.zone_type == ZoneType.IGNORED:
                # Suppress alert
                return {
                    "suppressed": True,
                    "reason": f"Detection inside IGNORED zone '{matched_zone.name}'"
                }

        # Match custom rule actions
        should_record = True
        should_notify = severity in (EventSeverity.MEDIUM, EventSeverity.HIGH, EventSeverity.CRITICAL)

        for rule in rules:
            if not rule.enabled:
                continue
            try:
                cond = json.loads(rule.conditions_json)
                actions = json.loads(rule.actions_json)
                if cond.get("object_class") in (object_class, "*"):
                    if confidence >= cond.get("min_confidence", 0.6):
                        if "severity" in actions:
                            severity = EventSeverity[actions["severity"]]
                        should_record = actions.get("record", should_record)
                        should_notify = actions.get("notify", should_notify)
            except Exception as e:
                logger.error(f"Error evaluating rule {rule.id}: {e}")

        return {
            "suppressed": False,
            "event_type": event_type,
            "severity": severity,
            "zone_id": matched_zone.id if matched_zone else None,
            "should_record": should_record,
            "should_notify": should_notify,
            "confidence": confidence,
            "track_id": track_id
        }

ai_rule_engine = AiRuleEngine()
