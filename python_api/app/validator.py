from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, Sequence

from app.normalization import normalize_word

WHITELIST_NORMALIZED: set[str] = set()
BLACKLIST_NORMALIZED: set[str] = set()


@dataclass(frozen=True, slots=True)
class ValidationResult:
    input: str
    normalized: str | None
    valid: bool

    def to_dict(self) -> dict[str, str | bool | None]:
        return asdict(self)


class LexiconValidator:
    def __init__(self, lexicon: Iterable[str]) -> None:
        self._lexicon = frozenset(token.strip() for token in lexicon if token.strip())

    @classmethod
    def from_file(cls, lexicon_path: Path) -> "LexiconValidator":
        if not lexicon_path.exists():
            raise FileNotFoundError(
                f"Generated lexicon file not found at {lexicon_path}. "
                "Run scripts/build_lexicon.py first."
            )

        with lexicon_path.open("r", encoding="utf-8") as file_handle:
            return cls(line.rstrip("\n") for line in file_handle)

    def validate(self, word: str) -> ValidationResult:
        normalized = normalize_word(word)
        if normalized is None:
            return ValidationResult(input=word, normalized=None, valid=False)

        if normalized in BLACKLIST_NORMALIZED:
            return ValidationResult(input=word, normalized=normalized, valid=False)

        if normalized in WHITELIST_NORMALIZED:
            return ValidationResult(input=word, normalized=normalized, valid=True)

        return ValidationResult(
            input=word,
            normalized=normalized,
            valid=normalized in self._lexicon,
        )

    def validate_batch(self, words: Sequence[str]) -> list[ValidationResult]:
        return [self.validate(word) for word in words]
