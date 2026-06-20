"""Mock moderation provider for local development.

Images/text that contain the literal string "UNSAFE_TEST" are treated as
flagged so that the review-queue flow can be exercised in dev without real media.
"""
import asyncio

from app.providers.base import ModerationLabel, ModerationProvider, ModerationResult

_UNSAFE_TRIGGER = "UNSAFE_TEST"
_CSAM_TRIGGER = "CSAM_TEST"


class MockModerationProvider(ModerationProvider):

    async def moderate_image(self, image_bytes: bytes) -> ModerationResult:
        await asyncio.sleep(0)              # yield to event loop (simulate async I/O)

        payload_str = image_bytes.decode("latin-1", errors="ignore")

        if _CSAM_TRIGGER in payload_str:
            return ModerationResult(
                safe=False,
                confidence=0.99,
                labels=[ModerationLabel(name="Explicit Nudity/CSAM", confidence=0.99)],
                is_csam=True,
                raw={"mock": True, "triggered_by": _CSAM_TRIGGER},
            )

        if _UNSAFE_TRIGGER in payload_str:
            return ModerationResult(
                safe=False,
                confidence=0.85,
                labels=[
                    ModerationLabel(name="Explicit Nudity", confidence=0.85),
                    ModerationLabel(name="Graphic Violence", confidence=0.30),
                ],
                raw={"mock": True, "triggered_by": _UNSAFE_TRIGGER},
            )

        return ModerationResult(
            safe=True,
            confidence=0.02,
            labels=[],
            raw={"mock": True},
        )

    async def moderate_text(self, text: str) -> ModerationResult:
        await asyncio.sleep(0)

        if _CSAM_TRIGGER in text:
            return ModerationResult(
                safe=False, confidence=0.99,
                labels=[ModerationLabel(name="CSAM", confidence=0.99)],
                is_csam=True,
                raw={"mock": True},
            )

        if _UNSAFE_TRIGGER in text:
            return ModerationResult(
                safe=False, confidence=0.80,
                labels=[ModerationLabel(name="HateSpeech", confidence=0.80)],
                raw={"mock": True},
            )

        return ModerationResult(safe=True, confidence=0.01, labels=[], raw={"mock": True})
