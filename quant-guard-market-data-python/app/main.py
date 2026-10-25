from importlib.metadata import PackageNotFoundError, version
import logging
import ipaddress
import os
import secrets

from fastapi import BackgroundTasks, FastAPI, Header, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app import __version__
from app.errors import ErrorCode, MarketDataError
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


@app.post("/internal/desktop/shutdown", status_code=202, include_in_schema=False)
def desktop_shutdown(
    request: Request,
    background_tasks: BackgroundTasks,
    shutdown_token: str | None = Header(
        default=None,
        alias="X-QuantGuard-Shutdown-Token",
    ),
) -> dict[str, str]:
    expected_token = os.getenv("QUANTGUARD_DESKTOP_SHUTDOWN_TOKEN", "")
    client_host = request.client.host if request.client else ""
    if not expected_token:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND)
    try:
        is_loopback = ipaddress.ip_address(client_host).is_loopback
    except ValueError:
        is_loopback = False
    if (
        not is_loopback
        or shutdown_token is None
        or not secrets.compare_digest(shutdown_token, expected_token)
    ):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN)
    shutdown_callback = getattr(app.state, "request_desktop_shutdown", None)
    if not callable(shutdown_callback):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Desktop shutdown is unavailable",
        )
    background_tasks.add_task(shutdown_callback)
    return {"status": "STOPPING"}


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


@app.exception_handler(MarketDataError)
async def market_data_exception_handler(_request: Request, exception: MarketDataError) -> JSONResponse:
    logging.getLogger(__name__).warning("Market-data request failed: code=%s message=%s", exception.code, exception.message)
    return JSONResponse(
        status_code=504 if exception.code == ErrorCode.TIMEOUT else 503,
        content={"code": exception.code.value, "message": exception.message},
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(_request: object, _exception: Exception) -> JSONResponse:
    logging.getLogger(__name__).error("Unhandled market-data error", exc_info=_exception)
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
