from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path
from typing import AsyncIterator

from fastapi import FastAPI, Request
from pydantic import BaseModel, Field

from app.validator import LexiconValidator

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_LEXICON_PATH = PROJECT_ROOT / "data" / "valid_words.txt"


class HealthResponse(BaseModel):
    status: str


class ValidationResponse(BaseModel):
    input: str
    normalized: str | None
    valid: bool


class BatchValidationRequest(BaseModel):
    words: list[str] = Field(default_factory=list)


def create_app(validator: LexiconValidator | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.validator = validator or LexiconValidator.from_file(DEFAULT_LEXICON_PATH)
        yield

    app = FastAPI(
        title="French Word Validation API",
        version="1.0.0",
        lifespan=lifespan,
    )

    def get_validator(request: Request) -> LexiconValidator:
        return request.app.state.validator

    @app.get("/health", response_model=HealthResponse)
    async def health() -> HealthResponse:
        return HealthResponse(status="ok")

    @app.get("/validate", response_model=ValidationResponse)
    async def validate(word: str, request: Request) -> ValidationResponse:
        result = get_validator(request).validate(word)
        return ValidationResponse(**result.to_dict())

    @app.post("/validate/batch", response_model=list[ValidationResponse])
    async def validate_batch(
        payload: BatchValidationRequest | list[str],
        request: Request,
    ) -> list[ValidationResponse]:
        words = payload.words if isinstance(payload, BatchValidationRequest) else payload
        results = get_validator(request).validate_batch(words)
        return [ValidationResponse(**result.to_dict()) for result in results]

    return app


app = create_app()
