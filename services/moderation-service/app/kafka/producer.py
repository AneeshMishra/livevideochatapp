"""Kafka producer — publishes moderation.events."""
import json
import logging
from datetime import datetime, timezone
from typing import Optional
from uuid import UUID

from aiokafka import AIOKafkaProducer

from app.config import get_settings

logger = logging.getLogger(__name__)


class ModerationProducer:

    def __init__(self) -> None:
        settings = get_settings()
        self._producer = AIOKafkaProducer(
            bootstrap_servers=settings.kafka_bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode(),
            key_serializer=lambda k: k.encode() if k else None,
            acks="all",
            enable_idempotence=True,
        )
        self._topic = settings.kafka_moderation_topic

    async def start(self) -> None:
        await self._producer.start()
        logger.info("Kafka moderation producer started")

    async def stop(self) -> None:
        await self._producer.stop()

    async def _publish(self, key: str, payload: dict) -> None:
        payload.setdefault("timestamp", datetime.now(timezone.utc).isoformat())
        try:
            await self._producer.send(self._topic, value=payload, key=key)
        except Exception as exc:
            logger.error("Failed to publish moderation event %s: %s", payload.get("type"), exc)

    async def publish_content_flagged(
        self,
        moderation_item_id: UUID,
        room_id: Optional[UUID],
        broadcaster_id: Optional[UUID],
        reason: str,
    ) -> None:
        await self._publish(
            key=str(room_id or moderation_item_id),
            payload={
                "type": "CONTENT_FLAGGED",
                "moderationItemId": str(moderation_item_id),
                "roomId": str(room_id) if room_id else None,
                "broadcasterId": str(broadcaster_id) if broadcaster_id else None,
                "reason": reason,
            },
        )

    async def publish_content_approved(
        self, moderation_item_id: UUID, room_id: Optional[UUID], broadcaster_id: Optional[UUID]
    ) -> None:
        await self._publish(
            key=str(room_id or moderation_item_id),
            payload={
                "type": "CONTENT_APPROVED",
                "moderationItemId": str(moderation_item_id),
                "roomId": str(room_id) if room_id else None,
                "broadcasterId": str(broadcaster_id) if broadcaster_id else None,
            },
        )

    async def publish_content_rejected(
        self,
        moderation_item_id: UUID,
        room_id: Optional[UUID],
        broadcaster_id: Optional[UUID],
        reason: str,
    ) -> None:
        await self._publish(
            key=str(room_id or moderation_item_id),
            payload={
                "type": "CONTENT_REJECTED",
                "moderationItemId": str(moderation_item_id),
                "roomId": str(room_id) if room_id else None,
                "broadcasterId": str(broadcaster_id) if broadcaster_id else None,
                "reason": reason,
            },
        )

    async def publish_stream_suspended(
        self, room_id: UUID, broadcaster_id: UUID, reason: str
    ) -> None:
        await self._publish(
            key=str(room_id),
            payload={
                "type": "STREAM_SUSPENDED",
                "roomId": str(room_id),
                "broadcasterId": str(broadcaster_id),
                "reason": reason,
            },
        )

    async def publish_csam_detected(
        self, moderation_item_id: UUID, room_id: Optional[UUID], broadcaster_id: Optional[UUID]
    ) -> None:
        """Critical: CSAM detection — triggers immediate escalation path."""
        await self._publish(
            key=str(room_id or moderation_item_id),
            payload={
                "type": "CSAM_DETECTED",
                "moderationItemId": str(moderation_item_id),
                "roomId": str(room_id) if room_id else None,
                "broadcasterId": str(broadcaster_id) if broadcaster_id else None,
                "severity": "CRITICAL",
            },
        )
