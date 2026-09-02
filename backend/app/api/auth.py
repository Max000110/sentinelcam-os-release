import time
import re
from typing import Dict, List
from fastapi import APIRouter, Depends, HTTPException, status, Request
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.core.database import get_db
from app.core.security import async_verify_password, async_get_password_hash, create_access_token, decode_access_token
from app.models.users import User, UserRole
from app.schemas.user import UserCreate, UserResponse, Token

router = APIRouter(prefix="/auth", tags=["Authentication"])
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")

# In-memory sliding window rate limiter for login brute force defense
# IP/Username -> list of failed attempt timestamps (seconds)
FAILED_ATTEMPTS: Dict[str, List[float]] = {}
MAX_FAILED_ATTEMPTS = 5
LOCKOUT_WINDOW_SECONDS = 300  # 5 minutes lockout

def _check_rate_limit(key: str):
    now = time.time()
    if key in FAILED_ATTEMPTS:
        # Clean older than lockout window
        FAILED_ATTEMPTS[key] = [t for t in FAILED_ATTEMPTS[key] if now - t < LOCKOUT_WINDOW_SECONDS]
        if len(FAILED_ATTEMPTS[key]) >= MAX_FAILED_ATTEMPTS:
            remaining = int(LOCKOUT_WINDOW_SECONDS - (now - FAILED_ATTEMPTS[key][0]))
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"Too many failed login attempts. Account temporarily locked for {max(1, remaining)} seconds."
            )

def _record_failed_attempt(key: str):
    now = time.time()
    if key not in FAILED_ATTEMPTS:
        FAILED_ATTEMPTS[key] = []
    FAILED_ATTEMPTS[key].append(now)

def _clear_failed_attempts(key: str):
    if key in FAILED_ATTEMPTS:
        del FAILED_ATTEMPTS[key]

async def get_current_user(token: str = Depends(oauth2_scheme), db: AsyncSession = Depends(get_db)) -> User:
    payload = decode_access_token(token)
    if payload is None or "sub" not in payload:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired authentication credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )
    username: str = payload["sub"]
    result = await db.execute(select(User).where(User.username == username))
    user = result.scalars().first()
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found")
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="User account is deactivated")
    return user

@router.post("/register", response_model=UserResponse)
async def register_user(user_in: UserCreate, db: AsyncSession = Depends(get_db)):
    if len(user_in.password) < 8:
        raise HTTPException(status_code=400, detail="Password must be at least 8 characters long")
    if not re.search(r"[0-9]", user_in.password) and not re.search(r"[\W_]", user_in.password):
        raise HTTPException(status_code=400, detail="Password must contain at least one number or special character")

    result = await db.execute(select(User).where((User.username == user_in.username) | (User.email == user_in.email)))
    existing_user = result.scalars().first()
    if existing_user:
        raise HTTPException(status_code=400, detail="Username or email already registered")
    
    hashed_pw = await async_get_password_hash(user_in.password)
    user = User(
        username=user_in.username,
        email=user_in.email,
        hashed_password=hashed_pw,
        role=UserRole.OPERATOR,
        is_active=True
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    return user

@router.post("/login", response_model=Token)
async def login(
    request: Request,
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: AsyncSession = Depends(get_db)
):
    client_ip = request.client.host if request.client else "unknown"
    rate_limit_key = f"{client_ip}:{form_data.username}"
    _check_rate_limit(rate_limit_key)

    result = await db.execute(select(User).where(User.username == form_data.username))
    user = result.scalars().first()
    if not user or not await async_verify_password(form_data.password, user.hashed_password):
        _record_failed_attempt(rate_limit_key)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
        
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="User account is deactivated")

    _clear_failed_attempts(rate_limit_key)
    access_token = create_access_token(subject=user.username)
    return {
        "access_token": access_token,
        "token_type": "bearer",
        "user": user
    }

@router.get("/me", response_model=UserResponse)
async def get_me(current_user: User = Depends(get_current_user)):
    return current_user
