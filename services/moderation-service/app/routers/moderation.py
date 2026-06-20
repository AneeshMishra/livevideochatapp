"""Frame / image / text submission endpoints (called by internal services)."""
from fastapi import APIRouter, Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.jwt import get_current_user
from app.database import get_db
from app.schemas import (
    FrameSubmitRequest,
    ImageSubmitRequest,
    ModerationResultResponse,
    TextSubmitRequest,
)
from app.services.ai_moderator import AIModerator

router = APIRouter(prefix="/api/v1/moderation", tags=["moderation"])


def _moderator(request: Request) -> AIModerator:
    return request.app.state.ai_moderator


@router.post("/frames", response_model=ModerationResultResponse)
async def submit_frame(
    body: FrameSubmitRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Submit a video frame (base64-encoded JPEG/PNG) for AI moderation.
    Called by the thumbnailer or frame-sampler workers — not end-user-facing.
    """
    return await _moderator(request).moderate_frame(
        db=db,
        image_b64=body.frame_b64,
        room_id=body.room_id,
        broadcaster_id=body.broadcaster_id,
    )


@router.post("/images", response_model=ModerationResultResponse)
async def submit_image(
    body: ImageSubmitRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Submit a static image (profile pic, thumbnail) for AI moderation."""
    return await _moderator(request).moderate_image(
        db=db,
        image_b64=body.image_b64,
        room_id=body.room_id,
        broadcaster_id=body.broadcaster_id,
        context=body.context,
    )


@router.post("/text", response_model=ModerationResultResponse)
async def submit_text(
    body: TextSubmitRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Submit a text snippet (chat message, profile bio) for moderation."""
    return await _moderator(request).moderate_text(
        db=db,
        text=body.text,
        room_id=body.room_id,
        broadcaster_id=body.broadcaster_id,
        submitter_id=body.submitter_id,
    )
