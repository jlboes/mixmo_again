from fastapi.testclient import TestClient
from pathlib import Path

from app.main import create_app
from app import validator as validator_module
from app.validator import LexiconValidator


def build_client() -> TestClient:
    validator = LexiconValidator({"ETE", "MANGEES", "BONJOUR"})
    return TestClient(create_app(validator=validator))


def test_health_endpoint() -> None:
    with build_client() as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_validate_endpoint_accepts_normalized_word() -> None:
    with build_client() as client:
        response = client.get("/validate", params={"word": "mangées"})

    assert response.status_code == 200
    assert response.json() == {
        "input": "mangées",
        "normalized": "MANGEES",
        "valid": True,
    }


def test_validate_endpoint_rejects_invalid_surface() -> None:
    with build_client() as client:
        response = client.get("/validate", params={"word": "aujourd’hui"})

    assert response.status_code == 200
    assert response.json() == {
        "input": "aujourd’hui",
        "normalized": None,
        "valid": False,
    }


def test_batch_validation_endpoint() -> None:
    with build_client() as client:
        response = client.post(
            "/validate/batch",
            json={"words": ["ÉTÉ", "chauffe-eau", "bonjour"]},
        )

    assert response.status_code == 200
    assert response.json() == [
        {"input": "ÉTÉ", "normalized": "ETE", "valid": True},
        {"input": "chauffe-eau", "normalized": None, "valid": False},
        {"input": "bonjour", "normalized": "BONJOUR", "valid": True},
    ]


def test_batch_validation_endpoint_accepts_raw_list() -> None:
    with build_client() as client:
        response = client.post(
            "/validate/batch",
            json=["ÉTÉ", "bonjour"],
        )

    assert response.status_code == 200
    assert response.json() == [
        {"input": "ÉTÉ", "normalized": "ETE", "valid": True},
        {"input": "bonjour", "normalized": "BONJOUR", "valid": True},
    ]


def test_blacklist_takes_precedence_over_whitelist() -> None:
    validator_module.WHITELIST_NORMALIZED.clear()
    validator_module.BLACKLIST_NORMALIZED.clear()
    validator_module.WHITELIST_NORMALIZED.add("TEST")
    validator_module.BLACKLIST_NORMALIZED.add("TEST")

    try:
        validator = LexiconValidator({"TEST"})
        result = validator.validate("test")
    finally:
        validator_module.WHITELIST_NORMALIZED.clear()
        validator_module.BLACKLIST_NORMALIZED.clear()

    assert result.valid is False
    assert result.normalized == "TEST"


def test_whitelist_accepts_word_outside_lexicon() -> None:
    validator_module.WHITELIST_NORMALIZED.clear()
    validator_module.BLACKLIST_NORMALIZED.clear()
    validator_module.WHITELIST_NORMALIZED.add("MIXMO")

    try:
        validator = LexiconValidator(set())
        result = validator.validate("mixmo")
    finally:
        validator_module.WHITELIST_NORMALIZED.clear()
        validator_module.BLACKLIST_NORMALIZED.clear()

    assert result.valid is True
    assert result.normalized == "MIXMO"


def test_generated_lexicon_contains_demo_seed_words() -> None:
    validator = LexiconValidator.from_file(Path("data/valid_words.txt"))

    for word in ("AIMER", "ANCRE", "AMOUR", "MICRO", "RIEUR"):
        result = validator.validate(word)
        assert result.valid is True
