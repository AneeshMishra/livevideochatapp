import pathlib
import logging
from typing import AsyncGenerator

from sqlalchemy import text
from sqlalchemy.ext.asyncio import (
    AsyncSession,
    create_async_engine,
    async_sessionmaker,
)

from app.config import get_settings

logger = logging.getLogger(__name__)

settings = get_settings()

engine = create_async_engine(
    settings.db_url,
    echo=False,
    pool_pre_ping=True,
    pool_size=10,
    max_overflow=5,
)

AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with AsyncSessionLocal() as session:
        yield session


async def run_migrations() -> None:
    migrations_dir = pathlib.Path(__file__).parent.parent / "migrations"
    sql_files = sorted(migrations_dir.glob("*.sql"))
    if not sql_files:
        logger.warning("No migration files found in %s", migrations_dir)
        return
    async with engine.begin() as conn:
        for sql_file in sql_files:
            logger.info("Applying migration: %s", sql_file.name)
            sql = sql_file.read_text(encoding="utf-8")
            await conn.execute(text(sql))
    logger.info("Migrations complete")


async def ping_db() -> None:
    async with engine.connect() as conn:
        await conn.execute(text("SELECT 1"))
