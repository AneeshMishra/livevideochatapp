"""Core AI moderation service.

Handles frame/image/text submission:
  1. Optionally store raw media in S3.
  2. Run through the configured AI provider.
  3. Persist a ModerationItem to PostgreSQL.
  4. Publish Kafka event if content is flagged or CSAM is detected.
  5. Write audit log entry.
"""
import base64
import logging
from datetime import datetime, timezone
from typing import Optional
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.kafka.producer import ModerationProducer
from app.models import AuditLog, ModerationItem
from app.providers.base import ModerationProvider
from app.schemas import ModerationResultResponse
from app.storage.s3 import S3Storage

logger = logging.getLogger(__name__)
settings = get_settings()


class AIModerator:

    def __init__(
        self,
        provider: ModerationProvider,
        producer: ModerationProducer,
        storage: S3Storage,
    ) -> None:
        self._provider = provider
        self._producer = producer
        self._storage = storage

    # ── Public entry points ────────────────────────────────────────────────────

    async def moderate_frame(
        self,
        db: AsyncSession,
        image_b64: str,
        room_id: UUID,
        broadcaster_id: UUID,
    ) -> ModerationResultResponse:
        image_bytes = self._decode_b64(image_b64)
        s3_key = await self._storage.upload_frame(image_bytes, str(room_id))
        result = await self._provider.moderate_image(image_bytes)
        return await self._persist_and_emit(
            db=db,
            content_type="FRAME",
            content_ref=s3_key,
            room_id=room_id,
            broadcaster_id=broadcaster_id,
            result=result,
        )

    async def moderate_image(
        self,
        db: AsyncSession,
        image_b64: str,
        room_id: Optional[UUID],
        broadcaster_id: Optional[UUID],
        context: Optional[str] = None,
    ) -> ModerationResultResponse:
        image_bytes = self._decode_b64(image_b64)
        s3_key = await self._storage.upload_image(image_bytes, context or "image")
        result = await self._provider.moderate_image(image_bytes)
        return await self._persist_and_emit(
            db=db,
            content_type="IMAGE",
            content_ref=s3_key,
            room_id=room_id,
            broadcaster_id=broadcaster_id,
            result=result,
        )

    async def moderate_text(
        self,
        db: AsyncSession,
        text: str,
        room_id: Optional[UUID],
        broadcaster_id: Optional[UUID],
        submitter_id: Optional[UUID],
    ) -> ModerationResultResponse:
        result = await self._provider.moderate_text(text)
        # Store only a short excerpt — never log full PII-bearing text in audit
        excerpt = (text[:100] + "...") if len(text) > 100 else text
        return await self._persist_and_emit(
            db=db,
            content_type="TEXT",
            content_ref=excerpt,
            room_id=room_id,
            broadcaster_id=broadcaster_id,
            result=result,
            submitter_id=submitter_id,
        )

    # ── Internal helpers ───────────────────────────────────────────────────────

    async def _persist_and_emit(
        self,
        db: AsyncSession,
        content_type: str,
        content_ref: Optional[str],
        room_id: Optional[UUID],
        broadcaster_id: Optional[UUID],
        result,
        submitter_id: Optional[UUID] = None,
    ) -> ModerationResultResponse:

        # CSAM detection — immediate escalation path (do not auto-approve under any threshold)
        if result.is_csam:
            status = "REJECTED"
        elif result.safe or result.confidence < settings.ai_unsafe_threshold:
            status = "AUTO_APPROVED"
        else:
            status = "PENDING"  # queue for human review

        item = ModerationItem(
            room_id=room_id,
            broadcaster_id=broadcaster_id,
            submitter_id=submitter_id,
            content_type=content_type,
            content_ref=content_ref,
            ai_provider=settings.moderation_provider,
            ai_result=result.raw,
            ai_safe=result.safe,
            ai_confidence=float(result.confidence),
            status=status,
            is_csam_flagged=result.is_csam,
        )
        db.add(item)

        audit = AuditLog(
            action="CONTENT_SCANNED",
            target_id=item.id,
            target_type="moderation_item",
            details={
                "content_type": content_type,
                "ai_safe": result.safe,
                "ai_confidence": float(result.confidence),
                "status": status,
                "is_csam": result.is_csam,
            },
        )
        db.add(audit)
        await db.commit()
        await db.refresh(item)

        # ── Kafka events ───────────────────────────────────────────────────────
        if result.is_csam:
            logger.critical(
                "CSAM DETECTED — item=%s room=%s broadcaster=%s",
                item.id, room_id, broadcaster_id,
            )
            await self._producer.publish_csam_detected(item.id, room_id, broadcaster_id)
            # Immediately suspend the stream if it belongs to a live room
            if room_id and broadcaster_id:
                await self._producer.publish_stream_suspended(
                    room_id, broadcaster_id, "CSAM_DETECTED"
                )
        elif status == "PENDING":
            await self._producer.publish_content_flagged(
                item.id, room_id, broadcaster_id,
                reason=", ".join(result.label_names[:3]),
            )

        return ModerationResultResponse(
            moderation_item_id=item.id,
            safe=result.safe,
            confidence=float(result.confidence),
            status=status,
            labels=result.label_names,
            is_csam_flagged=result.is_csam,
        )

    @staticmethod
    def _decode_b64(b64_str: str) -> bytes:
        # Strip data URI prefix if present (e.g. "data:image/jpeg;base64,...")
        if "," in b64_str:
            b64_str = b64_str.split(",", 1)[1]
        return base64.b64decode(b64_str)
