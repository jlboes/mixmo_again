from __future__ import annotations

import re
import unicodedata

FORBIDDEN_SURFACE_CHARACTERS = {"-", "'", "’", ".", " "}
NORMALIZED_TOKEN_PATTERN = re.compile(r"^[A-Z]+$")


def contains_forbidden_separators(value: str) -> bool:
    return any(character in value for character in FORBIDDEN_SURFACE_CHARACTERS)


def normalize_letters(value: str) -> str:
    replaced = value.upper().replace("Œ", "OE").replace("Æ", "AE")
    decomposed = unicodedata.normalize("NFKD", replaced)
    return "".join(character for character in decomposed if not unicodedata.combining(character))


def normalize_word(word: str) -> str | None:
    if not word or contains_forbidden_separators(word):
        return None

    normalized = normalize_letters(word)
    if not NORMALIZED_TOKEN_PATTERN.fullmatch(normalized):
        return None

    return normalized
