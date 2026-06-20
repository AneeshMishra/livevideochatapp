"""DMCA takedown notice endpoints."""
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.jwt import require_admin
from app.database import get_db
from app.schemas import DmcaActionRequest, DmcaCreateRequest, DmcaResponse, PagedResponse
from app.services.dmca_service import DmcaService

router = APIRouter(prefix="/api/v1/moderation/dmca", tags=["dmca"])


def _svc(request: Request) -> DmcaService:
    return request.app.state.dmca_service


@router.post("", response_model=DmcaResponse, status_code=201)
async def submit_dmca(
    body: DmcaCreateRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    """
    Public DMCA takedown notice submission.
    No authentication required — claimant provides contact details in the body.
    """
    return await _svc(request).create_notice(
        db=db,
        claimant_name=body.claimant_name,
        claimant_email=body.claimant_email,
        content_url=body.content_url,
        description=body.description,
        room_id=body.room_id,
        broadcaster_id=body.broadcaster_id,
    )


@router.get("", response_model=PagedResponse)
async def list_dmca(
    request: Request,
    status: Optional[str] = Query(None, pattern="^(RECEIVED|PROCESSING|ACTIONED|REJECTED)$"),
    page: int = 0,
    size: int = 20,
    db: AsyncSession = Depends(get_db),
    _admin: dict = Depends(require_admin),
):
    """ADMIN: list DMCA notices, optionally filtered by status."""
    svc: DmcaService = _svc(request)
    items, total = await svc.list_notices(db, status, page, min(size, 100))
    return PagedResponse(items=items, total=total, page=page, size=size)


@router.post("/{notice_id}/action", response_model=DmcaResponse)
async def action_dmca(
    notice_id: UUID,
    body: DmcaActionRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
    admin: dict = Depends(require_admin),
):
    """ADMIN: action (process or reject) a DMCA takedown notice."""
    try:
        return await _svc(request).action_notice(
            db=db,
            notice_id=notice_id,
            action=body.action,
            moderator_id=UUID(admin["sub"]),
        )
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc))
