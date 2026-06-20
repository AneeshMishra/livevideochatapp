"""Kafka consumer — monitors streaming.events to track active rooms."""
import asyncio
import json
import logging

from aiokafka import AIOKafkaConsumer

from app.config import get_settings

logger = logging.getLogger(__name__)


class StreamingEventConsumer:
    """
    Consumes streaming.events to maintain an in-memory set of active (LIVE) rooms.
    This set is used by the frame submission endpoint to validate that the room
    being moderated is actually live.
    """

    def __init__(self) -> None:
        settings = get_settings()
        self._consumer = AIOKafkaConsumer(
            settings.kafka_streaming_topic,
            bootstrap_servers=settings.kafka_bootstrap_servers,
            group_id=settings.kafka_group_id,
            auto_offset_reset="latest",   # only care about current state, not history
            enable_auto_commit=True,
            value_deserializer=lambda m: json.loads(m.decode("utf-8", errors="replace")),
        )
        self._active_rooms: set[str] = set()   # roomId strings

    async def start(self) -> None:
        await self._consumer.start()
        logger.info("Streaming event consumer started")

    async def stop(self) -> None:
        await self._consumer.stop()

    def is_room_active(self, room_id: str) -> bool:
        return room_id in self._active_rooms

    async def run(self) -> None:
        try:
            async for msg in self._consumer:
                try:
                    event = msg.value
                    event_type = event.get("type", "")
                    room_id = event.get("roomId")
                    if not room_id:
                        continue

                    if event_type == "STREAM_STARTED":
                        self._active_rooms.add(room_id)
                        logger.debug("Moderation: room %s is now active", room_id)
                    elif event_type in ("STREAM_ENDED", "STREAM_SUSPENDED"):
                        self._active_rooms.discard(room_id)
                        logger.debug("Moderation: room %s deactivated", room_id)

                except Exception as exc:
                    logger.warning("Error processing streaming event: %s", exc)
        except asyncio.CancelledError:
            logger.info("Streaming event consumer cancelled")
        except Exception as exc:
            logger.error("Streaming consumer fatal error: %s", exc, exc_info=True)
