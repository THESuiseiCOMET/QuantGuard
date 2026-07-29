from enum import StrEnum


class ErrorCode(StrEnum):
    INVALID_SYMBOL = "INVALID_SYMBOL"
    INSTRUMENT_NOT_FOUND = "INSTRUMENT_NOT_FOUND"
    UNSUPPORTED_INSTRUMENT_TYPE = "UNSUPPORTED_INSTRUMENT_TYPE"
    UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE"
    UPSTREAM_INVALID_RESPONSE = "UPSTREAM_INVALID_RESPONSE"
    NO_DATA = "NO_DATA"
    PARTIAL_FAILURE = "PARTIAL_FAILURE"


class MarketDataError(Exception):
    def __init__(self, code: ErrorCode, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
