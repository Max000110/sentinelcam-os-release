from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    PROJECT_NAME: str = "SentinelCam"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # Security / Auth
    JWT_SECRET_KEY: str = "<CHANGE_ME_JWT_SECRET>"
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24  # 24 hours
    
    # Database (Defaults to SQLite with aiosqlite for standalone portability, or PostgreSQL in production)
    DATABASE_URL: str = "sqlite+aiosqlite:///./sentinelcam.db"
    
    # Redis
    REDIS_URL: Optional[str] = "redis://localhost:6379/0"
    
    # Coturn STUN/TURN configuration
    COTURN_ENABLED: bool = True
    COTURN_PUBLIC_IP: str = "127.0.0.1"
    COTURN_PORT: int = 3478
    COTURN_TURNS_PORT: int = 5349
    COTURN_REALM: str = "sentinelcam.local"
    COTURN_STATIC_AUTH_SECRET: str = "<CHANGE_ME_TURN_SECRET>"
    TURN_CREDENTIAL_TTL_SECONDS: int = 86400  # 24 hours
    
    # Telegram Notifications
    TELEGRAM_BOT_TOKEN: Optional[str] = None
    TELEGRAM_CHAT_ID: Optional[str] = None

    model_config = SettingsConfigDict(case_sensitive=True, env_file=".env", extra="ignore")

settings = Settings()
