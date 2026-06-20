"""User-submitted report service."""
import logging
from datetime import datetime, timezone
from typing import Optional
from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AuditLog, Report
from app.schemas import ReportResponse

logger = logging.getLogger(__name__)


class ReportService:

    async def create_report(
        self,
        db: AsyncSession,
        reporter_id: UUID,
        reason: str,
        room_id: Optional[UUID],
        broadcaster_id: Optional[UUID],
        description: Optional[str],
    ) -> ReportResponse:
        report = Report(
            reporter_id=reporter_id,
            room_id=room_id,
            broadcaster_id=broadcaster_id,
            reason=reason,
            description=description,
            status="OPEN",
        )
        db.add(report)
        db.add(AuditLog(
            action="REPORT_CREATED",
            actor_id=reporter_id,
            target_id=report.id,
            target_type="report",
            details={"reason": reason, "broadcaster_id": str(broadcaster_id) if broadcaster_id else None},
        ))
        await db.commit()
        await db.refresh(report)
        logger.info("Report created: %s reason=%s room=%s", report.id, reason, room_id)
        return ReportResponse.model_validate(report)

    async def list_reports(
        self, db: AsyncSession, status: Optional[str], page: int, size: int
    ) -> tuple[list[ReportResponse], int]:
        base_q = select(Report)
        count_q = select(func.count()).select_from(Report)
        if status:
            base_q = base_q.where(Report.status == status)
            count_q = count_q.where(Report.status == status)

        rows = (await db.execute(
            base_q.order_by(Report.created_at.desc()).offset(page * size).limit(size)
        )).scalars().all()
        total = (await db.execute(count_q)).scalar_one()

        return [ReportResponse.model_validate(r) for r in rows], total

    async def resolve_report(
        self,
        db: AsyncSession,
        report_id: UUID,
        resolution: str,       # RESOLVED | DISMISSED
        moderator_id: UUID,
    ) -> ReportResponse:
        result = await db.execute(select(Report).where(Report.id == report_id))
        report = result.scalar_one_or_none()
        if report is None:
            raise ValueError(f"Report {report_id} not found")

        report.status = resolution
        report.resolved_by = moderator_id
        report.resolved_at = datetime.now(timezone.utc)

        db.add(AuditLog(
            action=f"REPORT_{resolution}",
            actor_id=moderator_id,
            target_id=report_id,
            target_type="report",
            details={"resolution": resolution},
        ))
        await db.commit()
        await db.refresh(report)
        logger.info("Report %s resolved as %s by %s", report_id, resolution, moderator_id)
        return ReportResponse.model_validate(report)
