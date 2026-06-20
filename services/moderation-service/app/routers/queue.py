"""Human review queue endpoints (ADMIN only)."""
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.jwt import require_admin
from app.database import get_db
from app.schemas import ModerationItemResponse, PagedResponse, ReviewDecisionRequest
from app.services.review_queue import ReviewQueueService

router = APIRouter(prefix="/api/v1/moderation/queue", tags=["review-queue"])


def _queue_svc(request: Request) -> ReviewQueueService:
    return request.app.state.review_queue


@router.get("", response_model=PagedResponse)
async def list_pending(
    request: Request,
    page: int = 0,
    size: int = 20,
    db: AsyncSession = Depends(get_db),
    admin: dict = Depends(require_admin),
):
    """List content items awaiting human review, oldest-first."""
    svc: ReviewQueueService = _queue_svc(request)
    items, total = await svc.get_pending(db, page, min(size, 100))
    return PagedResponse(items=items, total=total, page=page, size=size)


@router.get("/{item_id}", response_model=ModerationItemResponse)
async def get_item(
    item_id: UUID,
    request: Request,
    db: AsyncSession = Depends(get_db),
    admin: dict = Depends(require_admin),
):
    svc: ReviewQueueService = _queue_svc(request)
    item = await svc.get_item(db, item_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Item not found")
    return ModerationItemResponse.model_validate(item)


@router.post("/{item_id}/decide", response_model=ModerationItemResponse)
async def decide(
    item_id: UUID,
    body: ReviewDecisionRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
    admin: dict = Depends(require_admin),
):
    """Approve, reject, or escalate a pending moderation item."""
    svc: ReviewQueueService = _queue_svc(request)
    try:
        return await svc.decide(
            db=db,
            item_id=item_id,
            decision=body.decision,
            notes=body.notes,
            moderator_id=UUID(admin["sub"]),
        )
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc))
