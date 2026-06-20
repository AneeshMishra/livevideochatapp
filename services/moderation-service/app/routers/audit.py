"""Audit log endpoint (read-only, ADMIN only)."""
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.jwt import require_admin
from app.database import get_db
from app.models import AuditLog
from app.schemas import AuditLogResponse, PagedResponse

router = APIRouter(prefix="/api/v1/moderation/audit", tags=["audit"])


@router.get("", response_model=PagedResponse)
async def get_audit_log(
    request: Request,
    target_id: Optional[UUID] = Query(None),
    target_type: Optional[str] = Query(None),
    page: int = 0,
    size: int = 50,
    db: AsyncSession = Depends(get_db),
    _admin: dict = Depends(require_admin),
):
    """ADMIN: query the immutable audit log."""
    query = select(AuditLog).order_by(AuditLog.created_at.desc())
    if target_id:
        query = query.where(AuditLog.target_id == target_id)
    if target_type:
        query = query.where(AuditLog.target_type == target_type)

    rows = (await db.execute(query.offset(page * size).limit(min(size, 200)))).scalars().all()
    return PagedResponse(
        items=[AuditLogResponse.model_validate(r) for r in rows],
        total=len(rows),
        page=page,
        size=size,
    )
