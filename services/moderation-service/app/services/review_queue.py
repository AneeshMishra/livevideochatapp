"""Human review queue service.

Moderators fetch pending items, then approve or reject them.
Each decision is recorded in the audit_log.
"""
import logging
from datetime import datetime, timezone
from typing import Optional
from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.kafka.producer import ModerationProducer
from app.models import AuditLog, ModerationItem
from app.schemas import ModerationItemResponse

logger = logging.getLogger(__name__)


class ReviewQueueService:

    def __init__(self, producer: ModerationProducer) -> None:
        self._producer = producer

    async def get_pending(
        self, db: AsyncSession, page: int, size: int
    ) -> tuple[list[ModerationItemResponse], int]:
        offset = page * size
        query = (
            select(ModerationItem)
            .where(ModerationItem.status == "PENDING")
            .order_by(ModerationItem.created_at.asc())
            .offset(offset)
            .limit(size)
        )
        total_q = select(func.count()).select_from(ModerationItem).where(ModerationItem.status == "PENDING")

        rows = (await db.execute(query)).scalars().all()
        total = (await db.execute(total_q)).scalar_one()

        items = [ModerationItemResponse.model_validate(row) for row in rows]
        return items, total

    async def get_item(self, db: AsyncSession, item_id: UUID) -> Optional[ModerationItem]:
        result = await db.execute(
            select(ModerationItem).where(ModerationItem.id == item_id)
        )
        return result.scalar_one_or_none()

    async def decide(
        self,
        db: AsyncSession,
        item_id: UUID,
        decision: str,           # APPROVED | REJECTED | ESCALATED
        notes: Optional[str],
        moderator_id: UUID,
    ) -> ModerationItemResponse:
        item = await self.get_item(db, item_id)
        if item is None:
            raise ValueError(f"ModerationItem {item_id} not found")
        if item.status not in ("PENDING", "ESCALATED"):
            raise ValueError(f"Item {item_id} is already {item.status}")

        item.status = decision
        item.reviewed_by = moderator_id
        item.review_notes = notes
        item.reviewed_at = datetime.now(timezone.utc)

        audit = AuditLog(
            action=f"REVIEW_{decision}",
            actor_id=moderator_id,
            target_id=item_id,
            target_type="moderation_item",
            details={"notes": notes, "room_id": str(item.room_id), "broadcaster_id": str(item.broadcaster_id)},
        )
        db.add(audit)
        await db.commit()
        await db.refresh(item)

        # ── Kafka notifications ────────────────────────────────────────────────
        if decision == "APPROVED":
            await self._producer.publish_content_approved(
                item.id, item.room_id, item.broadcaster_id
            )
        elif decision == "REJECTED":
            await self._producer.publish_content_rejected(
                item.id, item.room_id, item.broadcaster_id,
                reason=notes or "Rejected by moderator",
            )
            # If the room is still live, suspend the stream
            if item.room_id and item.broadcaster_id:
                await self._producer.publish_stream_suspended(
                    item.room_id, item.broadcaster_id, "MODERATION_REJECTION"
                )

        logger.info("Review decision: item=%s decision=%s moderator=%s", item_id, decision, moderator_id)
        return ModerationItemResponse.model_validate(item)
