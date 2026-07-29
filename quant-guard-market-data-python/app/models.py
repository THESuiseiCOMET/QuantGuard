from datetime import date
from typing import Literal

from pydantic import BaseModel, Field, model_validator

from app.errors import ErrorCode


BatchStatus = Literal["SUCCESS", "PARTIAL_FAILURE", "FAILED"]
InstrumentType = Literal["STOCK", "ETF", "INDEX", "UNKNOWN"]


class SymbolBatchRequest(BaseModel):
    symbols: list[str] = Field(min_length=1, max_length=100)


class InstrumentSearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=64)
    limit: int = Field(default=20, ge=1, le=50)


class DailyBarsRequest(SymbolBatchRequest):
    start_date: date
    end_date: date

    @model_validator(mode="after")
    def validate_date_range(self) -> "DailyBarsRequest":
        if self.start_date > self.end_date:
            raise ValueError("start_date must not be later than end_date")
        return self


class ErrorDetail(BaseModel):
    code: ErrorCode
    message: str


class InstrumentMetadata(BaseModel):
    symbol: str
    name: str
    exchange: Literal["SSE", "SZSE"]
    currency: str = "CNY"
    list_status: str
    list_date: date | None
    instrument_type: InstrumentType


class InstrumentLookupResult(BaseModel):
    symbol: str
    instrument: InstrumentMetadata | None = None
    error: ErrorDetail | None = None


class InstrumentLookupResponse(BaseModel):
    status: BatchStatus
    results: list[InstrumentLookupResult]


class InstrumentSearchResponse(BaseModel):
    results: list[InstrumentMetadata]


class DailyBar(BaseModel):
    symbol: str
    trade_date: date
    open: str
    high: str
    low: str
    close: str
    previous_close: str | None
    change: str | None
    percentage_change: str | None
    volume_lots: str
    amount_cny: str


class DailyBarsResult(BaseModel):
    symbol: str
    bars: list[DailyBar] = Field(default_factory=list)
    error: ErrorDetail | None = None


class DailyBarsResponse(BaseModel):
    status: BatchStatus
    results: list[DailyBarsResult]
