import json
from typing import List, Dict, Any, Optional
from pydantic import BaseModel
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.models.devices import Device
from app.models.zones_and_rules import Rule

router = APIRouter(prefix="/rules", tags=["Rule Engine"])

class RuleCreate(BaseModel):
    device_id: str
    name: str
    conditions: Dict[str, Any] # {"object_class": "person", "min_confidence": 0.75}
    actions: Dict[str, Any] # {"record": true, "notify": true, "severity": "HIGH"}

@router.get("/device/{device_id}")
async def get_device_rules(device_id: str, db: AsyncSession = Depends(get_db)):
    dev_res = await db.execute(select(Device).where(Device.device_id == device_id))
    device = dev_res.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
        
    rules_res = await db.execute(select(Rule).where(Rule.device_db_id == device.id))
    rules = rules_res.scalars().all()
    return [
        {
            "id": r.id,
            "name": r.name,
            "enabled": r.enabled,
            "conditions": json.loads(r.conditions_json),
            "actions": json.loads(r.actions_json)
        }
        for r in rules
    ]

@router.post("")
async def create_rule(rule_in: RuleCreate, db: AsyncSession = Depends(get_db)):
    dev_res = await db.execute(select(Device).where(Device.device_id == rule_in.device_id))
    device = dev_res.scalars().first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    rule = Rule(
        device_db_id=device.id,
        name=rule_in.name,
        enabled=True,
        conditions_json=json.dumps(rule_in.conditions),
        actions_json=json.dumps(rule_in.actions)
    )
    db.add(rule)
    await db.commit()
    await db.refresh(rule)
    return {
        "id": rule.id,
        "name": rule.name,
        "enabled": rule.enabled,
        "conditions": rule_in.conditions,
        "actions": rule_in.actions
    }

@router.delete("/{rule_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_rule(rule_id: int, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(Rule).where(Rule.id == rule_id))
    rule = res.scalars().first()
    if not rule:
        raise HTTPException(status_code=404, detail="Rule not found")
    await db.delete(rule)
    await db.commit()
    return None
