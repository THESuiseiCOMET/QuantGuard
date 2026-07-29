from importlib.metadata import PackageNotFoundError, version

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app import __version__
from app.errors import ErrorCode
from app.models import (
    DailyBarsRequest,
    DailyBarsResponse,
    InstrumentLookupResponse,
    InstrumentSearchRequest,
    InstrumentSearchResponse,
    SymbolBatchRequest,
)
from app.service import MarketDataService


app = FastAPI(title="QuantGuard Market Data Python", version=__version__)
service = MarketDataService()


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "UP",
        "baostock": _package_version("baostock"),
        "akshare": _package_version("akshare"),
    }


@app.post("/v1/instruments/lookup", response_model=InstrumentLookupResponse)
def lookup_instruments(request: SymbolBatchRequest) -> InstrumentLookupResponse:
    return service.lookup_instruments(request.symbols)


@app.post("/v1/instruments/search", response_model=InstrumentSearchResponse)
def search_instruments(request: InstrumentSearchRequest) -> InstrumentSearchResponse:
    return service.search_instruments(request.query, request.limit)


@app.post("/v1/daily-bars", response_model=DailyBarsResponse)
def daily_bars(request: DailyBarsRequest) -> DailyBarsResponse:
    return service.daily_bars(request.symbols, request.start_date, request.end_date)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    _request: object,
    _exception: RequestValidationError,
) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content={
            "code": ErrorCode.INVALID_SYMBOL.value,
            "message": "Invalid market-data request",
        },
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(_request: object, _exception: Exception) -> JSONResponse:
    return JSONResponse(
        status_code=500,
        content={
            "code": ErrorCode.UPSTREAM_UNAVAILABLE.value,
            "message": "Market-data service failed to process the request",
        },
    )


def _package_version(package: str) -> str:
    try:
        return version(package)
    except PackageNotFoundError:
        return "unknown"
