"""AWS Rekognition moderation provider.

Wraps boto3 (sync) calls in an asyncio thread-pool executor.
Rekognition is used for image content moderation; text moderation falls back
to a keyword-based approach (Rekognition does not expose a general text safety API).
"""
import asyncio
import logging
from typing import Optional

import boto3

from app.providers.base import ModerationLabel, ModerationProvider, ModerationResult

logger = logging.getLogger(__name__)

# Rekognition label names that indicate CSAM
_CSAM_LABELS = frozenset({"Explicit Nudity/CSAM", "Explicit Sexual Activity"})

# Labels that indicate minors or unsafe content for underage
_UNDERAGE_LABELS = frozenset({"Partial Nudity", "Revealing Clothes"})

# Rekognition ModerationLabel categories to treat as high confidence
_HIGH_SEVERITY = frozenset({
    "Explicit Nudity",
    "Non-Consensual",
    "Graphic Violence or Gore",
})


class RekognitionModerationProvider(ModerationProvider):

    def __init__(
        self,
        aws_region: str,
        aws_access_key_id: Optional[str] = None,
        aws_secret_access_key: Optional[str] = None,
    ) -> None:
        kwargs: dict = {"region_name": aws_region}
        if aws_access_key_id:
            kwargs["aws_access_key_id"] = aws_access_key_id
        if aws_secret_access_key:
            kwargs["aws_secret_access_key"] = aws_secret_access_key
        self._client = boto3.client("rekognition", **kwargs)
        self._loop = asyncio.get_event_loop()

    async def moderate_image(self, image_bytes: bytes) -> ModerationResult:
        response = await asyncio.get_event_loop().run_in_executor(
            None, self._detect_labels, image_bytes
        )
        return self._parse_response(response)

    def _detect_labels(self, image_bytes: bytes) -> dict:
        try:
            return self._client.detect_moderation_labels(
                Image={"Bytes": image_bytes},
                MinConfidence=30.0,  # cast a wide net; we filter by threshold in the service layer
            )
        except Exception as exc:
            logger.error("Rekognition call failed: %s", exc)
            # Fail open — do not block the stream; queue for human review instead.
            return {"ModerationLabels": [], "error": str(exc)}

    def _parse_response(self, response: dict) -> ModerationResult:
        raw_labels = response.get("ModerationLabels", [])
        labels = [
            ModerationLabel(
                name=lbl["Name"],
                confidence=lbl["Confidence"] / 100.0,
                parent_name=lbl.get("ParentName", ""),
            )
            for lbl in raw_labels
        ]

        is_csam = any(lbl.name in _CSAM_LABELS for lbl in labels)
        max_confidence = max((lbl.confidence for lbl in labels), default=0.0)
        safe = len(labels) == 0 or max_confidence < 0.30

        return ModerationResult(
            safe=safe,
            confidence=max_confidence,
            labels=labels,
            is_csam=is_csam,
            raw=response,
        )

    async def moderate_text(self, text: str) -> ModerationResult:
        """
        Rekognition does not offer a general-purpose text moderation API.
        Phase 1: basic keyword detection. Phase 3+: integrate AWS Comprehend
        or a dedicated text safety vendor (Hive AI, Perspective API).
        """
        lower = text.lower()
        # Minimal prohibited term detection for Phase 1
        if any(kw in lower for kw in ("csam", "child porn", "minor nude")):
            return ModerationResult(
                safe=False,
                confidence=0.99,
                labels=[ModerationLabel(name="Prohibited Text/CSAM", confidence=0.99)],
                is_csam=True,
                raw={"provider": "keyword_filter"},
            )
        return ModerationResult(
            safe=True, confidence=0.0, labels=[], raw={"provider": "keyword_filter"}
        )
