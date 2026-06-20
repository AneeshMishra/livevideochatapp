"""Abstract AI moderation provider interface."""
from abc import ABC, abstractmethod
from dataclasses import dataclass, field


@dataclass
class ModerationLabel:
    name: str
    confidence: float       # 0.0 – 1.0
    parent_name: str = ""


@dataclass
class ModerationResult:
    safe: bool
    confidence: float               # highest unsafe label confidence
    labels: list[ModerationLabel] = field(default_factory=list)
    is_csam: bool = False           # true if CSAM-specific label detected
    raw: dict = field(default_factory=dict)

    @property
    def label_names(self) -> list[str]:
        return [lbl.name for lbl in self.labels]


class ModerationProvider(ABC):

    @abstractmethod
    async def moderate_image(self, image_bytes: bytes) -> ModerationResult: ...

    @abstractmethod
    async def moderate_text(self, text: str) -> ModerationResult: ...
