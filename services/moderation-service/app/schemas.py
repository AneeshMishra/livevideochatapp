"""Pydantic v2 request/response schemas."""
from __future__ import annotations

from datetime import datetime
from typing import Any, Optional
from uuid import UUID

from pydantic import BaseModel, EmailStr, Field


# ── Moderation / frame submission ──────────────────────────────────────────────

class FrameSubmitRequest(BaseModel):
    room_id: UUID
    broadcaster_id: UUID
    frame_b64: str = Field(..., description="Base64-encoded JPEG/PNG frame")


class ImageSubmitRequest(BaseModel):
    room_id: Optional[UUID] = None
    broadcaster_id: Optional[UUID] = None
    image_b64: str
    context: Optional[str] = None    # "profile_image", "thumbnail", etc.


class TextSubmitRequest(BaseModel):
    room_id: Optional[UUID] = None
    broadcaster_id: Optional[UUID] = None
    submitter_id: Optional[UUID] = None
    text: str = Field(..., max_length=2000)


class ModerationResultResponse(BaseModel):
    moderation_item_id: UUID
    safe: bool
    confidence: float
    status: str
    labels: list[str] = []
    is_csam_flagged: bool = False


# ── Review queue ───────────────────────────────────────────────────────────────

class ReviewDecisionRequest(BaseModel):
    decision: str = Field(..., pattern="^(APPROVED|REJECTED|ESCALATED)$")
    notes: Optional[str] = None


class ModerationItemResponse(BaseModel):
    id: UUID
    room_id: Optional[UUID]
    broadcaster_id: Optional[UUID]
    content_type: str
    content_ref: Optional[str]
    ai_safe: Optional[bool]
    ai_confidence: Optional[float]
    ai_result: Optional[dict[str, Any]]
    status: str
    is_csam_flagged: bool
    created_at: datetime
    reviewed_at: Optional[datetime]
    review_notes: Optional[str]

    model_config = {"from_attributes": True}


# ── Reports ────────────────────────────────────────────────────────────────────

class ReportCreateRequest(BaseModel):
    room_id: Optional[UUID] = None
    broadcaster_id: Optional[UUID] = None
    reason: str = Field(
        ...,
        pattern="^(CSAM|UNDERAGE|NON_CONSENSUAL|SPAM|HARASSMENT|HATE_SPEECH|ILLEGAL|OTHER)$",
    )
    description: Optional[str] = Field(None, max_length=2000)


class ReportResponse(BaseModel):
    id: UUID
    reporter_id: UUID
    room_id: Optional[UUID]
    broadcaster_id: Optional[UUID]
    reason: str
    description: Optional[str]
    status: str
    created_at: datetime
    resolved_at: Optional[datetime]

    model_config = {"from_attributes": True}


class ReportResolveRequest(BaseModel):
    resolution: str = Field(..., pattern="^(RESOLVED|DISMISSED)$")


# ── DMCA ───────────────────────────────────────────────────────────────────────

class DmcaCreateRequest(BaseModel):
    claimant_name: str = Field(..., max_length=200)
    claimant_email: str = Field(..., max_length=200)
    content_url: str = Field(..., max_length=2000)
    description: str = Field(..., max_length=5000)
    room_id: Optional[UUID] = None
    broadcaster_id: Optional[UUID] = None


class DmcaResponse(BaseModel):
    id: UUID
    claimant_name: str
    claimant_email: str
    content_url: str
    status: str
    room_id: Optional[UUID]
    broadcaster_id: Optional[UUID]
    created_at: datetime
    actioned_at: Optional[datetime]

    model_config = {"from_attributes": True}


class DmcaActionRequest(BaseModel):
    action: str = Field(..., pattern="^(ACTIONED|REJECTED)$")


# ── Audit ──────────────────────────────────────────────────────────────────────

class AuditLogResponse(BaseModel):
    id: int
    action: str
    actor_id: Optional[UUID]
    target_id: Optional[UUID]
    target_type: Optional[str]
    details: Optional[dict[str, Any]]
    created_at: datetime

    model_config = {"from_attributes": True}


# ── Pagination helper ──────────────────────────────────────────────────────────

class PagedResponse(BaseModel):
    items: list[Any]
    total: int
    page: int
    size: int
