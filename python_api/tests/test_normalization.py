from app.normalization import normalize_word


def test_normalize_strips_accents() -> None:
    assert normalize_word("ÉTÉ") == "ETE"


def test_normalize_plural_feminine_form() -> None:
    assert normalize_word("MANGÉES") == "MANGEES"


def test_normalize_ligatures() -> None:
    assert normalize_word("ŒUVRE") == "OEUVRE"
    assert normalize_word("ÆTHER") == "AETHER"


def test_rejects_apostrophe() -> None:
    assert normalize_word("aujourd’hui") is None


def test_rejects_hyphen() -> None:
    assert normalize_word("chauffe-eau") is None


def test_rejects_dot() -> None:
    assert normalize_word("M.") is None


def test_rejects_spaces() -> None:
    assert normalize_word("DON JUAN") is None
