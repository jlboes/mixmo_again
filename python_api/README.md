# French Word Validation API

FastAPI service that validates French words against a normalized lexicon generated from `Morphalou3.1_CSV.csv`.

Scope:

- normalize a submitted token
- validate it against the in-memory lexicon

It does not know about board layout, coordinates, players, rooms, or the Mixmo orchestration flow. Spring remains responsible for board traversal, extracted-word aggregation, and final Mixmo authorization.

## Requirements

- Python 3.12+
- FastAPI
- Uvicorn
- No database

## Project Structure

```text
python_api/
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── normalization.py
│   └── validator.py
├── data/
│   └── valid_words.txt
├── scripts/
│   └── build_lexicon.py
├── tests/
│   ├── test_normalization.py
│   └── test_validation.py
├── Morphalou3.1_CSV.csv
├── README.md
└── requirements.txt
```

## Build The Lexicon

From the `python_api` directory:

```bash
python3 scripts/build_lexicon.py
```

This script:

- reads `Morphalou3.1_CSV.csv` as semicolon-separated CSV
- skips the introductory lines before the real header
- uses the `FLEXION/GRAPHIE` column at index `9`
- rejects proper nouns, abbreviations, sigles, chemical symbols, and tokens with hyphens, apostrophes, dots, or spaces
- normalizes accepted words to uppercase ASCII
- writes `data/valid_words.txt`
- prints build stats

## Run The API

Install dependencies:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Start the server:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Endpoints

- `GET /health`
- `GET /validate?word=...`
- `POST /validate/batch`

Normalization and rejection rules:

- uppercase output
- `Œ -> OE`
- `Æ -> AE`
- accents and diacritics removed
- only `A-Z` allowed after normalization
- reject hyphens, apostrophes, dots, and spaces
- whitelist and blacklist hooks remain available but empty by default

Batch request body:

```json
{
  "words": ["été", "mangées", "bonjour"]
}
```

Example response item:

```json
{
  "input": "mangées",
  "normalized": "MANGEES",
  "valid": true
}
```

## Tests

Run the test suite from `python_api`:

```bash
.venv/bin/pytest
```
