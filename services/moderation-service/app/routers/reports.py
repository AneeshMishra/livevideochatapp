"""User-submitted report endpoints."""
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.jwt import get_current_user, require_admin
from app.database import get_db
from app.schemas import PagedResponse, ReportCreateRequest, ReportResolveRequest, ReportResponse
from app.services.report_service import ReportService

router = APIRouter(prefix="/api/v1/moderation/reports", tags=["reports"])


def _svc(request: Request) -> ReportService:
    return request.app.state.report_service


@router.post("", response_model=ReportResponse, status_code=201)
async def create_report(
    body: ReportCreateRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
    user: dict = Depends(get_current_user),
):
    """Authenticated viewer submits a report against a room or broadcaster."""
    return await _svc(request).create_report(
        db=db,
        reporter_id=UUID(user["sub"]),
        reason=body.reason,
        room_id=body.room_id,
        broadcaster_id=body.broadcaster_id,
        description=body.description,
    )


@router.get("", response_model=PagedResponse)
async def list_reports(
    request: Request,
    status: Optional[str] = Query(None, pattern="^(OPEN|INVESTIGATING|RESOLVED|DISMISSED)$"),
    page: int = 0,
    size: int = 20,
    db: AsyncSession = Depends(get_db),
    _admin: dict = Depends(require_admin),
):
    """ADMIN: list reports, optionally filtered by status."""
    svc: ReportService = _svc(request)
    items, total = await svc.list_reports(db, status, page, min(size, 100))
    return PagedResponse(items=items, total=total, page=page, size=size)


@router.post("/{report_id}/resolve", response_model=ReportResponse)
async def resolve_report(
    report_id: UUID,
    body: ReportResolveRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
    admin: dict = Depends(require_admin),
):
    """ADMIN: resolve or dismiss a report."""
    try:
        return await _svc(request).resolve_report(
            db=db,
            report_id=report_id,
            resolution=body.resolution,
            moderator_id=UUID(admin["sub"]),
        )
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc))
