"""DMCA takedown notice service.

Receives takedown notices, queues them for processing, and records actions.
In production, ACTIONED should also trigger:
  - Removal of the content from S3 / CDN
  - Notification to the broadcaster
  - Recording in the compliance audit trail
All of these are best done via Kafka events consumed by dedicated services.
"""
import logging
from datetime import datetime, timezone
from typing import Optional
from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AuditLog, DmcaNotice
from app.schemas import DmcaResponse

logger = logging.getLogger(__name__)


class DmcaService:

    async def create_notice(
        self,
        db: AsyncSession,
        claimant_name: str,
        claimant_email: str,
        content_url: str,
        description: str,
        room_id: Optional[UUID],
        broadcaster_id: Optional[UUID],
    ) -> DmcaResponse:
        notice = DmcaNotice(
            claimant_name=claimant_name,
            claimant_email=claimant_email,
            content_url=content_url,
            description=description,
            room_id=room_id,
            broadcaster_id=broadcaster_id,
            status="RECEIVED",
        )
        db.add(notice)
        db.add(AuditLog(
            action="DMCA_NOTICE_RECEIVED",
            target_id=notice.id,
            target_type="dmca_notice",
            details={"claimant_email": claimant_email, "content_url": content_url},
        ))
        await db.commit()
        await db.refresh(notice)
        logger.info("DMCA notice received: %s from %s", notice.id, claimant_email)
        return DmcaResponse.model_validate(notice)

    async def list_notices(
        self, db: AsyncSession, status: Optional[str], page: int, size: int
    ) -> tuple[list[DmcaResponse], int]:
        base_q = select(DmcaNotice)
        count_q = select(func.count()).select_from(DmcaNotice)
        if status:
            base_q = base_q.where(DmcaNotice.status == status)
            count_q = count_q.where(DmcaNotice.status == status)

        rows = (await db.execute(
            base_q.order_by(DmcaNotice.created_at.desc()).offset(page * size).limit(size)
        )).scalars().all()
        total = (await db.execute(count_q)).scalar_one()

        return [DmcaResponse.model_validate(r) for r in rows], total

    async def action_notice(
        self,
        db: AsyncSession,
        notice_id: UUID,
        action: str,            # ACTIONED | REJECTED
        moderator_id: UUID,
    ) -> DmcaResponse:
        result = await db.execute(select(DmcaNotice).where(DmcaNotice.id == notice_id))
        notice = result.scalar_one_or_none()
        if notice is None:
            raise ValueError(f"DMCA notice {notice_id} not found")

        notice.status = action
        notice.handled_by = moderator_id
        notice.actioned_at = datetime.now(timezone.utc)

        db.add(AuditLog(
            action=f"DMCA_{action}",
            actor_id=moderator_id,
            target_id=notice_id,
            target_type="dmca_notice",
            details={"action": action, "content_url": notice.content_url},
        ))
        await db.commit()
        await db.refresh(notice)
        logger.info("DMCA notice %s %s by %s", notice_id, action, moderator_id)
        return DmcaResponse.model_validate(notice)
