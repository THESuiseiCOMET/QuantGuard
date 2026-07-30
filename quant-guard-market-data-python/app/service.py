from datetime import date, datetime
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from threading import Lock
from time import monotonic
from typing import Any, Iterable

import pandas as pd

from app.clients import AkShareClient, BaoStockClient, CatalogInstrument, RawInstrumentMetadata
from app.errors import ErrorCode, MarketDataError
from app.models import (
    DailyBar,
    DailyBarsResponse,
    DailyBarsResult,
    ErrorDetail,
    InstrumentLookupResponse,
    InstrumentLookupResult,
    InstrumentMetadata,
    InstrumentSearchResponse,
)
from app.symbols import CanonicalSymbol


_TYPE_MAP = {
    "1": "STOCK",
    "2": "INDEX",
    "5": "ETF",
}
_PERCENT_SCALE = Decimal("0.000001")
_CATALOG_TTL_SECONDS = 6 * 60 * 60


class MarketDataService:
    def __init__(
        self,
        baostock_client: BaoStockClient | None = None,
        akshare_client: AkShareClient | None = None,
    ) -> None:
        self.baostock = baostock_client or BaoStockClient()
        self.akshare = akshare_client or AkShareClient()
        self._catalog_cache: tuple[float, list[CatalogInstrument]] | None = None
        self._catalog_lock = Lock()

    def lookup_instruments(self, raw_symbols: Iterable[str]) -> InstrumentLookupResponse:
        results: list[InstrumentLookupResult] = []
        for raw_symbol in raw_symbols:
            display_symbol = (raw_symbol or "").strip().upper()
            try:
                symbol = CanonicalSymbol.parse(raw_symbol)
                metadata = self._lookup_one(symbol)
                results.append(
                    InstrumentLookupResult(symbol=symbol.canonical, instrument=metadata)
                )
            except MarketDataError as exc:
                results.append(
                    InstrumentLookupResult(
                        symbol=display_symbol,
                        error=ErrorDetail(code=exc.code, message=exc.message),
                    )
                )
        return InstrumentLookupResponse(status=_batch_status(results), results=results)

    def search_instruments(self, query: str, limit: int) -> InstrumentSearchResponse:
        normalized = query.strip().upper()
        matches = [
            item
            for item in self._catalog_entries()
            if normalized in item.symbol.code
            or normalized in item.symbol.canonical
            or normalized in item.metadata.name.upper()
        ]
        matches.sort(key=lambda item: _catalog_sort_key(item, normalized))
        return InstrumentSearchResponse(
            results=[self._metadata(item.symbol, item.metadata) for item in matches[:limit]]
        )

    def daily_bars(
        self,
        raw_symbols: Iterable[str],
        start_date: date,
        end_date: date,
    ) -> DailyBarsResponse:
        results: list[DailyBarsResult] = []
        for raw_symbol in raw_symbols:
            display_symbol = (raw_symbol or "").strip().upper()
            try:
                symbol = CanonicalSymbol.parse(raw_symbol)
                metadata = self._lookup_raw(symbol)
                instrument_type = _TYPE_MAP.get(metadata.instrument_type, "UNKNOWN")
                if instrument_type == "STOCK":
                    bars = self._stock_bars(symbol, start_date, end_date)
                elif instrument_type == "ETF":
                    bars = self._etf_bars(symbol, start_date, end_date)
                elif instrument_type == "INDEX":
                    bars = self._index_bars(symbol, start_date, end_date)
                else:
                    raise MarketDataError(
                        ErrorCode.UNSUPPORTED_INSTRUMENT_TYPE,
                        f"Unsupported instrument type for {symbol.canonical}",
                    )
                if not bars:
                    raise MarketDataError(
                        ErrorCode.NO_DATA,
                        f"No daily bars returned for {symbol.canonical}",
                    )
                results.append(DailyBarsResult(symbol=symbol.canonical, bars=bars))
            except MarketDataError as exc:
                results.append(
                    DailyBarsResult(
                        symbol=display_symbol,
                        error=ErrorDetail(code=exc.code, message=exc.message),
                    )
                )
        return DailyBarsResponse(status=_batch_status(results), results=results)

    def _lookup_one(self, symbol: CanonicalSymbol) -> InstrumentMetadata:
        raw = self._lookup_raw(symbol)
        return self._metadata(symbol, raw)

    def _metadata(
        self,
        symbol: CanonicalSymbol,
        raw: RawInstrumentMetadata,
    ) -> InstrumentMetadata:
        return InstrumentMetadata(
            symbol=symbol.canonical,
            name=raw.name or symbol.canonical,
            exchange=symbol.exchange,
            currency="CNY",
            list_status="L" if raw.status == "1" else "D",
            list_date=_optional_date(raw.list_date, "ipoDate"),
            instrument_type=_TYPE_MAP.get(raw.instrument_type, "UNKNOWN"),
        )

    def _lookup_raw(self, symbol: CanonicalSymbol) -> RawInstrumentMetadata:
        cached_metadata = self._cached_catalog_metadata(symbol)
        if cached_metadata is not None:
            return cached_metadata

        primary_error: MarketDataError | None = None
        try:
            return self.baostock.lookup_instrument(symbol)
        except MarketDataError as exc:
            primary_error = exc
        for item in self._catalog_entries():
            if item.symbol == symbol:
                return item.metadata
        if (
            primary_error is not None
            and primary_error.code != ErrorCode.INSTRUMENT_NOT_FOUND
        ):
            raise primary_error
        raise MarketDataError(
            ErrorCode.INSTRUMENT_NOT_FOUND,
            f"Instrument not found: {symbol.canonical}",
        )

    def _cached_catalog_metadata(
        self,
        symbol: CanonicalSymbol,
    ) -> RawInstrumentMetadata | None:
        now = monotonic()
        with self._catalog_lock:
            if self._catalog_cache is None:
                return None
            cached_at, catalog = self._catalog_cache
            if now - cached_at >= _CATALOG_TTL_SECONDS:
                return None
            for item in catalog:
                if item.symbol == symbol:
                    return item.metadata
        return None

    def _catalog_entries(self) -> list[CatalogInstrument]:
        now = monotonic()
        with self._catalog_lock:
            if self._catalog_cache is not None and now - self._catalog_cache[0] < _CATALOG_TTL_SECONDS:
                return self._catalog_cache[1]

            merged: dict[str, CatalogInstrument] = {}
            errors: list[MarketDataError] = []
            for loader in (
                self.baostock.list_instruments,
                self.akshare.stock_catalog,
                self.akshare.index_catalog,
            ):
                try:
                    for item in loader():
                        merged[item.symbol.canonical] = item
                except MarketDataError as exc:
                    errors.append(exc)
            if not merged:
                if errors:
                    raise errors[0]
                raise MarketDataError(
                    ErrorCode.NO_DATA,
                    "No instruments are available in the provider catalog",
                )
            catalog = list(merged.values())
            self._catalog_cache = (monotonic(), catalog)
            return catalog

    def _stock_bars(
        self,
        symbol: CanonicalSymbol,
        start_date: date,
        end_date: date,
    ) -> list[DailyBar]:
        try:
            frame = self.baostock.daily_bars(symbol, start_date, end_date)
        except MarketDataError as exc:
            if exc.code != ErrorCode.UPSTREAM_UNAVAILABLE:
                raise
            frame = self.akshare.stock_daily_bars(symbol)
            return self._sina_bars(
                frame,
                symbol,
                start_date,
                end_date,
                require_amount=True,
            )
        if frame.empty:
            return []
        required = {"date", "open", "high", "low", "close", "preclose", "volume", "amount"}
        if not required.issubset(frame.columns):
            raise MarketDataError(
                ErrorCode.UPSTREAM_INVALID_RESPONSE,
                f"BaoStock daily response missing fields for {symbol.canonical}",
            )
        rows: dict[date, dict[str, Any]] = {}
        for item in frame.to_dict("records"):
            if str(item.get("tradestatus", "1")).strip() != "1":
                continue
            trade_date = _required_date(item["date"], "date")
            rows[trade_date] = item
        bars: list[DailyBar] = []
        for trade_date in sorted(rows):
            item = rows[trade_date]
            previous = _optional_decimal(item["preclose"], "preclose")
            close = _required_decimal(item["close"], "close")
            change, percentage = _change_values(close, previous)
            bars.append(
                DailyBar(
                    symbol=symbol.canonical,
                    trade_date=trade_date,
                    open=_decimal_string(_required_decimal(item["open"], "open")),
                    high=_decimal_string(_required_decimal(item["high"], "high")),
                    low=_decimal_string(_required_decimal(item["low"], "low")),
                    close=_decimal_string(close),
                    previous_close=_decimal_string(previous) if previous is not None else None,
                    change=_decimal_string(change) if change is not None else None,
                    percentage_change=_decimal_string(percentage) if percentage is not None else None,
                    volume_lots=_decimal_string(
                        _required_decimal(item["volume"], "volume") / Decimal("100")
                    ),
                    amount_cny=_decimal_string(_required_decimal(item["amount"], "amount")),
                )
            )
        return bars

    def _etf_bars(
        self,
        symbol: CanonicalSymbol,
        start_date: date,
        end_date: date,
    ) -> list[DailyBar]:
        frame = self.akshare.etf_daily_bars(symbol)
        return self._sina_bars(frame, symbol, start_date, end_date, require_amount=True)

    def _index_bars(
        self,
        symbol: CanonicalSymbol,
        start_date: date,
        end_date: date,
    ) -> list[DailyBar]:
        frame = self.akshare.index_daily_bars(symbol)
        return self._sina_bars(frame, symbol, start_date, end_date, require_amount=False)

    def _sina_bars(
        self,
        frame: pd.DataFrame,
        symbol: CanonicalSymbol,
        start_date: date,
        end_date: date,
        *,
        require_amount: bool,
    ) -> list[DailyBar]:
        if frame.empty:
            return []
        columns = _resolve_columns(frame.columns, require_amount=require_amount)
        rows: dict[date, dict[str, Any]] = {}
        for item in frame.to_dict("records"):
            trade_date = _required_date(item[columns["date"]], "date")
            rows[trade_date] = item

        complete: list[DailyBar] = []
        previous: Decimal | None = None
        for trade_date in sorted(rows):
            item = rows[trade_date]
            close = _required_decimal(item[columns["close"]], "close")
            change, percentage = _change_values(close, previous)
            complete.append(
                DailyBar(
                    symbol=symbol.canonical,
                    trade_date=trade_date,
                    open=_decimal_string(_required_decimal(item[columns["open"]], "open")),
                    high=_decimal_string(_required_decimal(item[columns["high"]], "high")),
                    low=_decimal_string(_required_decimal(item[columns["low"]], "low")),
                    close=_decimal_string(close),
                    previous_close=_decimal_string(previous) if previous is not None else None,
                    change=_decimal_string(change) if change is not None else None,
                    percentage_change=_decimal_string(percentage) if percentage is not None else None,
                    volume_lots=_decimal_string(
                        _required_decimal(item[columns["volume"]], "volume") / Decimal("100")
                    ),
                    amount_cny=_decimal_string(
                        _required_decimal(item[columns["amount"]], "amount")
                        if "amount" in columns
                        else Decimal("0")
                    ),
                )
            )
            previous = close
        return [bar for bar in complete if start_date <= bar.trade_date <= end_date]


def _batch_status(results: list[Any]) -> str:
    failures = sum(1 for item in results if item.error is not None)
    if failures == 0:
        return "SUCCESS"
    if failures == len(results):
        return "FAILED"
    return ErrorCode.PARTIAL_FAILURE.value


def _catalog_sort_key(
    item: CatalogInstrument,
    query: str,
) -> tuple[int, str, str]:
    code = item.symbol.code
    canonical = item.symbol.canonical
    name = item.metadata.name.upper()
    if query == canonical or query == code:
        rank = 0
    elif code.startswith(query) or canonical.startswith(query):
        rank = 1
    elif name.startswith(query):
        rank = 2
    elif query in code or query in canonical:
        rank = 3
    else:
        rank = 4
    return rank, canonical, name


def _resolve_columns(
    columns: Iterable[Any],
    *,
    require_amount: bool = True,
) -> dict[str, Any]:
    aliases = {
        "date": ("date", "日期"),
        "open": ("open", "开盘"),
        "high": ("high", "最高"),
        "low": ("low", "最低"),
        "close": ("close", "收盘"),
        "volume": ("volume", "成交量"),
        "amount": ("amount", "成交额"),
    }
    if not require_amount:
        aliases.pop("amount")
    available = {str(column).strip().lower(): column for column in columns}
    resolved: dict[str, Any] = {}
    for field, names in aliases.items():
        for name in names:
            actual = available.get(name.lower())
            if actual is not None:
                resolved[field] = actual
                break
        if field not in resolved:
            raise MarketDataError(
                ErrorCode.UPSTREAM_INVALID_RESPONSE,
                f"AKShare Sina response missing field: {field}",
            )
    return resolved


def _required_decimal(value: Any, field: str) -> Decimal:
    decimal = _optional_decimal(value, field)
    if decimal is None:
        raise MarketDataError(
            ErrorCode.UPSTREAM_INVALID_RESPONSE,
            f"Upstream response has an empty decimal field: {field}",
        )
    return decimal


def _optional_decimal(value: Any, field: str) -> Decimal | None:
    if value is None or (isinstance(value, str) and not value.strip()) or pd.isna(value):
        return None
    try:
        result = Decimal(str(value).strip())
    except (InvalidOperation, ValueError) as exc:
        raise MarketDataError(
            ErrorCode.UPSTREAM_INVALID_RESPONSE,
            f"Upstream response has an invalid decimal field: {field}",
        ) from exc
    if not result.is_finite():
        raise MarketDataError(
            ErrorCode.UPSTREAM_INVALID_RESPONSE,
            f"Upstream response has a non-finite decimal field: {field}",
        )
    return result


def _change_values(
    close: Decimal,
    previous: Decimal | None,
) -> tuple[Decimal | None, Decimal | None]:
    if previous is None:
        return None, None
    change = close - previous
    if previous == 0:
        return change, None
    percentage = ((change / previous) * Decimal("100")).quantize(
        _PERCENT_SCALE,
        rounding=ROUND_HALF_UP,
    )
    return change, percentage


def _decimal_string(value: Decimal) -> str:
    text = format(value, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def _required_date(value: Any, field: str) -> date:
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    try:
        return date.fromisoformat(str(value).strip()[:10])
    except (TypeError, ValueError) as exc:
        raise MarketDataError(
            ErrorCode.UPSTREAM_INVALID_RESPONSE,
            f"Upstream response has an invalid date field: {field}",
        ) from exc


def _optional_date(value: Any, field: str) -> date | None:
    if value is None or not str(value).strip():
        return None
    return _required_date(value, field)
