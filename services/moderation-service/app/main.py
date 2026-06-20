"""FastAPI application entry-point for the moderation service."""
import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.database import ping_db, run_migrations
from app.kafka.consumer import StreamingEventConsumer
from app.kafka.producer import ModerationProducer
from app.providers.mock import MockModerationProvider
from app.providers.rekognition import RekognitionModerationProvider
from app.routers import audit, dmca, moderation, queue, reports
from app.services.ai_moderator import AIModerator
from app.services.dmca_service import DmcaService
from app.services.report_service import ReportService
from app.services.review_queue import ReviewQueueService
from app.storage.s3 import S3Storage

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)
settings = get_settings()


def _build_provider():
    if settings.moderation_provider == "REKOGNITION":
        logger.info("Using AWS Rekognition moderation provider")
        return RekognitionModerationProvider(
            aws_region=settings.aws_region,
            aws_access_key_id=settings.aws_access_key_id or None,
            aws_secret_access_key=settings.aws_secret_access_key or None,
        )
    logger.info("Using Mock moderation provider (dev mode)")
    return MockModerationProvider()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # ── Startup ────────────────────────────────────────────────────────────────
    logger.info("moderation-service starting (provider=%s)", settings.moderation_provider)

    await run_migrations()

    producer = ModerationProducer()
    await producer.start()

    consumer = StreamingEventConsumer()
    await consumer.start()
    consumer_task = asyncio.create_task(consumer.run())

    storage = S3Storage()
    try:
        storage.ensure_bucket()
    except Exception as exc:
        logger.warning("S3 bucket check failed (continuing): %s", exc)

    provider = _build_provider()

    # Attach shared singletons to app.state for dependency access in routers
    app.state.ai_moderator   = AIModerator(provider, producer, storage)
    app.state.review_queue   = ReviewQueueService(producer)
    app.state.report_service = ReportService()
    app.state.dmca_service   = DmcaService()
    app.state.consumer       = consumer

    logger.info("moderation-service ready on port %s", settings.port)

    yield

    # ── Shutdown ───────────────────────────────────────────────────────────────
    consumer_task.cancel()
    try:
        await consumer_task
    except asyncio.CancelledError:
        pass
    await consumer.stop()
    await producer.stop()
    logger.info("moderation-service shut down")


app = FastAPI(
    title="Moderation Service",
    description=(
        "AI-assisted content moderation: frame scanning, CSAM detection, "
        "human review queue, DMCA takedowns, and immutable audit log."
    ),
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],   # tighten per-environment via WAF/ingress
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Routers ────────────────────────────────────────────────────────────────────
app.include_router(moderation.router)
app.include_router(queue.router)
app.include_router(reports.router)
app.include_router(dmca.router)
app.include_router(audit.router)


# ── Health / readiness ─────────────────────────────────────────────────────────

@app.get("/health", tags=["ops"])
async def health():
    return {"status": "UP", "service": "moderation-service"}


@app.get("/ready", tags=["ops"])
async def ready():
    try:
        await ping_db()
        return {"status": "READY"}
    except Exception as exc:
        from fastapi import Response
        return Response(
            content='{"status":"DOWN","detail":"' + str(exc) + '"}',
            status_code=503,
            media_type="application/json",
        )
