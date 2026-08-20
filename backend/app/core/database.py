from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import declarative_base
from app.core.config import settings

from sqlalchemy.pool import NullPool

# Use connection pooling for PostgreSQL (major perf win), NullPool only for SQLite
_is_postgres = settings.DATABASE_URL.startswith("postgresql")

_engine_kwargs = dict(
    echo=False,
    future=True,
)

if _is_postgres:
    # Async connection pool: avoids creating a new TCP connection per query
    _engine_kwargs.update(
        pool_size=10,         # Maintained idle connections
        max_overflow=20,      # Burst capacity above pool_size
        pool_pre_ping=True,   # Detect stale connections before use
        pool_recycle=1800,    # Recycle connections every 30 min (firewall/proxy safety)
    )
else:
    _engine_kwargs["poolclass"] = NullPool

engine = create_async_engine(settings.DATABASE_URL, **_engine_kwargs)

AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False
)

Base = declarative_base()

async def get_db():
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()

async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
