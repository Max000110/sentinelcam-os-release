from typing import List
from fastapi import HTTPException, status, Depends
from app.models.users import User, UserRole
from app.api.auth import get_current_user

def require_roles(allowed_roles: List[UserRole]):
    def role_checker(current_user: User = Depends(get_current_user)) -> User:
        if current_user.role not in allowed_roles and current_user.role != UserRole.OWNER:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"User role '{current_user.role.value}' does not have sufficient permission for this action."
            )
        return current_user
    return role_checker

def verify_device_ownership(user_id: int, device_user_id: int, user_role: UserRole) -> bool:
    if user_role in (UserRole.OWNER, UserRole.ADMIN):
        return True
    return user_id == device_user_id
