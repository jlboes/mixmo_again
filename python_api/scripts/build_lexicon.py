from __future__ import annotations

import argparse
import csv
import sys
from dataclasses import dataclass
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.normalization import contains_forbidden_separators, normalize_letters, normalize_word

HEADER_PREFIX = "GRAPHIE;ID;CATÉGORIE;"
SURFACE_FORM_INDEX = 9


@dataclass(slots=True)
class LemmaContext:
    lemma: str = ""
    category: str = ""
    subcategory: str = ""
    locution: str = ""


@dataclass(frozen=True, slots=True)
class BuildStats:
    total_rows_read: int
    accepted_rows: int
    unique_normalized_words: int


def find_header_line(csv_path: Path) -> int:
    with csv_path.open("r", encoding="utf-8", newline="") as file_handle:
        for index, line in enumerate(file_handle):
            if line.startswith(HEADER_PREFIX):
                return index

    raise RuntimeError(f"Could not find Morphalou header in {csv_path}")


def letters_only(value: str) -> str:
    normalized = normalize_letters(value)
    return "".join(character for character in normalized if "A" <= character <= "Z")


def looks_like_abbreviation_or_symbol(
    surface: str,
    lemma_context: LemmaContext,
    row: list[str],
) -> bool:
    if lemma_context.subcategory == "abréviation":
        return True

    if row[11] != "invariable":
        return False

    surface_normalized = normalize_word(surface)
    lemma_letters = letters_only(lemma_context.lemma)
    if surface_normalized is None or not lemma_letters or surface_normalized == lemma_letters:
        return False

    if len(surface_normalized) <= 5 and len(lemma_letters) <= 5:
        return True

    return len(surface_normalized) <= 5 and len(surface_normalized) + 2 <= len(lemma_letters)


def is_valid_surface_form(surface: str, lemma_context: LemmaContext, row: list[str]) -> bool:
    if not surface:
        return False

    if lemma_context.locution:
        return False

    if any(character.isupper() for character in surface):
        return False

    if contains_forbidden_separators(surface):
        return False

    if looks_like_abbreviation_or_symbol(surface, lemma_context, row):
        return False

    return normalize_word(surface) is not None


def iter_surface_forms(csv_path: Path) -> tuple[int, list[str]]:
    header_line = find_header_line(csv_path)
    total_rows_read = 0
    normalized_words: list[str] = []
    lemma_context = LemmaContext()

    with csv_path.open("r", encoding="utf-8", newline="") as file_handle:
        for _ in range(header_line + 1):
            next(file_handle)

        reader = csv.reader(file_handle, delimiter=";")
        for row in reader:
            total_rows_read += 1
            if len(row) <= SURFACE_FORM_INDEX:
                continue

            if row[0]:
                lemma_context = LemmaContext(
                    lemma=row[0],
                    category=row[2],
                    subcategory=row[3],
                    locution=row[4],
                )

            surface = row[SURFACE_FORM_INDEX]
            if not is_valid_surface_form(surface, lemma_context, row):
                continue

            normalized = normalize_word(surface)
            if normalized is not None:
                normalized_words.append(normalized)

    return total_rows_read, normalized_words


def build_lexicon(input_path: Path, output_path: Path) -> BuildStats:
    total_rows_read, normalized_words = iter_surface_forms(input_path)
    unique_words = sorted(set(normalized_words))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as file_handle:
        file_handle.write("\n".join(unique_words))
        file_handle.write("\n")

    return BuildStats(
        total_rows_read=total_rows_read,
        accepted_rows=len(normalized_words),
        unique_normalized_words=len(unique_words),
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build a normalized French lexicon from Morphalou.")
    parser.add_argument(
        "--input",
        type=Path,
        default=PROJECT_ROOT / "Morphalou3.1_CSV.csv",
        help="Path to Morphalou3.1_CSV.csv",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=PROJECT_ROOT / "data" / "valid_words.txt",
        help="Output path for the normalized lexicon",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    stats = build_lexicon(args.input, args.output)
    print(f"Total rows read: {stats.total_rows_read}")
    print(f"Accepted rows: {stats.accepted_rows}")
    print(f"Unique normalized words: {stats.unique_normalized_words}")


if __name__ == "__main__":
    main()
