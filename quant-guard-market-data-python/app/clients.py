import socket
from dataclasses import dataclass
from datetime import date
from threading import Lock

import akshare as ak
import baostock as bs
import baostock.common.contants as bs_constants
import pandas as pd

from app.errors import ErrorCode, MarketDataError
from app.symbols import CanonicalSymbol
from app.upstream import bounded_upstream


_BAOSTOCK_SESSION_LOCK = Lock()
_BAOSTOCK_CONNECT_TIMEOUT_SECONDS = 3


@dataclass(frozen=True)
class RawInstrumentMetadata:
    name: str
    list_date: str
    instrument_type: str
    status: str


@dataclass(frozen=True)
class CatalogInstrument:
    symbol: CanonicalSymbol
    metadata: RawInstrumentMetadata


class BaoStockClient:
    def __init__(self, *, isolated: bool = True):
        self._isolated = isolated

    _DAILY_FIELDS = (
        "date,code,open,high,low,close,preclose,volume,amount,"
        "adjustflag,tradestatus,pctChg"
    )

    @bounded_upstream(timeout=8)
    def lookup_instrument(self, symbol: CanonicalSymbol) -> RawInstrumentMetadata:
        with _BAOSTOCK_SESSION_LOCK:
            self._ensure_available()
            login_result = bs.login()
            try:
                self._require_success(login_result, "BaoStock login failed", ErrorCode.UPSTREAM_UNAVAILABLE)
                result = bs.query_stock_basic(code=symbol.baostock_code)
                rows = self._read_rows(result, "BaoStock instrument response is invalid")
                if not rows:
                    raise MarketDataError(
                        ErrorCode.INSTRUMENT_NOT_FOUND,
                        f"Instrument not found: {symbol.canonical}",
                    )
                row = dict(zip(result.fields, rows[0], strict=True))
                return RawInstrumentMetadata(
                    name=(row.get("code_name") or "").strip(),
                    list_date=(row.get("ipoDate") or "").strip(),
                    instrument_type=(row.get("type") or "").strip(),
                    status=(row.get("status") or "").strip(),
                )
            except MarketDataError:
                raise
            except Exception as exc:
                raise MarketDataError(
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    f"BaoStock instrument lookup failed for {symbol.canonical}",
                ) from exc
            finally:
                bs.logout()

    @bounded_upstream(timeout=8)
    def search_instruments(self, query: str) -> list[CatalogInstrument]:
        normalized = query.strip().upper()
        if normalized[:6].isdigit():
            suffixes = [normalized[7:].lower()] if len(normalized) == 9 else ["sh", "sz"]
            queries = [{"code": f"{suffix}.{normalized[:6]}"} for suffix in suffixes]
        else:
            queries = [{"code_name": query}]
        with _BAOSTOCK_SESSION_LOCK:
            self._ensure_available()
            login_result = bs.login()
            try:
                self._require_success(login_result, "BaoStock login failed", ErrorCode.UPSTREAM_UNAVAILABLE)
                instruments = []
                for params in queries:
                    result = bs.query_stock_basic(**params)
                    for values in self._read_rows(result, "BaoStock instrument search response is invalid"):
                        row = dict(zip(result.fields, values, strict=True))
                        if row.get("type") not in {"1", "2", "5"}:
                            continue
                        try:
                            symbol = CanonicalSymbol.from_baostock_code(row.get("code") or "")
                        except MarketDataError:
                            continue
                        instruments.append(CatalogInstrument(symbol, RawInstrumentMetadata(
                            name=(row.get("code_name") or "").strip(),
                            list_date=(row.get("ipoDate") or "").strip(),
                            instrument_type=row["type"], status=(row.get("status") or "").strip(),
                        )))
                return instruments
            except MarketDataError:
                raise
            except Exception as exc:
                raise MarketDataError(ErrorCode.UPSTREAM_UNAVAILABLE, "BaoStock instrument search failed") from exc
            finally:
                bs.logout()

    @bounded_upstream(timeout=8)
    def daily_bars(
        self,
        symbol: CanonicalSymbol,
        start_date: date,
        end_date: date,
    ) -> pd.DataFrame:
        with _BAOSTOCK_SESSION_LOCK:
            self._ensure_available()
            login_result = bs.login()
            try:
                self._require_success(login_result, "BaoStock login failed", ErrorCode.UPSTREAM_UNAVAILABLE)
                result = bs.query_history_k_data_plus(
                    symbol.baostock_code,
                    self._DAILY_FIELDS,
                    start_date=start_date.isoformat(),
                    end_date=end_date.isoformat(),
                    frequency="d",
                    adjustflag="3",
                )
                rows = self._read_rows(result, "BaoStock daily response is invalid")
                return pd.DataFrame(rows, columns=result.fields)
            except MarketDataError:
                raise
            except Exception as exc:
                raise MarketDataError(
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    f"BaoStock daily query failed for {symbol.canonical}",
                ) from exc
            finally:
                bs.logout()

    def _read_rows(self, result: object, message: str) -> list[list[str]]:
        self._require_success(result, message, ErrorCode.UPSTREAM_INVALID_RESPONSE)
        rows: list[list[str]] = []
        while result.next():
            rows.append(result.get_row_data())
        self._require_success(result, message, ErrorCode.UPSTREAM_INVALID_RESPONSE)
        return rows

    def _ensure_available(self) -> None:
        try:
            with socket.create_connection(
                (bs_constants.BAOSTOCK_SERVER_IP, bs_constants.BAOSTOCK_SERVER_PORT),
                timeout=_BAOSTOCK_CONNECT_TIMEOUT_SECONDS,
            ):
                return
        except OSError as exc:
            raise MarketDataError(
                ErrorCode.UPSTREAM_UNAVAILABLE,
                "BaoStock service is unreachable",
            ) from exc

    def _require_success(self, result: object, message: str, code: ErrorCode) -> None:
        if getattr(result, "error_code", None) != "0":
            detail = getattr(result, "error_msg", "")
            raise MarketDataError(code, f"{message}: {detail}".rstrip(": "))


class AkShareClient:
    def __init__(self, *, isolated: bool = True):
        self._isolated = isolated

    @bounded_upstream(timeout=12)
    def stock_catalog(self) -> list[CatalogInstrument]:
        try:
            frame = ak.stock_info_a_code_name()
        except Exception as exc:
            raise MarketDataError(
                ErrorCode.UPSTREAM_UNAVAILABLE,
                "AKShare A-share catalog query failed",
            ) from exc
        if not isinstance(frame, pd.DataFrame) or not {"code", "name"}.issubset(
            frame.columns
        ):
            raise MarketDataError(
                ErrorCode.UPSTREAM_INVALID_RESPONSE,
                "AKShare returned an invalid A-share catalog response",
            )
        instruments: list[CatalogInstrument] = []
        for item in frame.to_dict("records"):
            code = str(item.get("code") or "").strip()
            suffix = (
                "SH"
                if code.startswith("6")
                else "SZ"
                if code.startswith(("0", "3"))
                else None
            )
            if suffix is None:
                continue
            try:
                symbol = CanonicalSymbol.parse(f"{code}.{suffix}")
            except MarketDataError:
                continue
            instruments.append(
                CatalogInstrument(
                    symbol=symbol,
                    metadata=RawInstrumentMetadata(
                        name=str(item.get("name") or "").strip(),
                        list_date="",
                        instrument_type="1",
                        status="1",
                    ),
                )
            )
        return instruments

    @bounded_upstream(timeout=12)
    def etf_catalog(self) -> list[CatalogInstrument]:
        try:
            frame = ak.fund_etf_category_sina(symbol="ETF基金")
            if not isinstance(frame, pd.DataFrame) or not {"代码", "名称"}.issubset(frame.columns):
                raise MarketDataError(ErrorCode.UPSTREAM_INVALID_RESPONSE, "Invalid AKShare ETF catalog response")
            return [CatalogInstrument(CanonicalSymbol.from_sina_code(str(row["代码"])),
                        RawInstrumentMetadata(str(row["名称"]), "", "5", "1"))
                    for row in frame.to_dict("records")]
        except MarketDataError:
            raise
        except Exception as exc:
            raise MarketDataError(ErrorCode.UPSTREAM_UNAVAILABLE, "AKShare ETF catalog query failed") from exc

    @bounded_upstream(timeout=12)
    def stock_daily_bars(self, symbol: CanonicalSymbol) -> pd.DataFrame:
        try:
            frame = ak.stock_zh_a_daily(symbol=symbol.sina_code, adjust="")
        except Exception as exc:
            raise MarketDataError(
                ErrorCode.UPSTREAM_UNAVAILABLE,
                f"AKShare Sina stock query failed for {symbol.canonical}",
            ) from exc
        if not isinstance(frame, pd.DataFrame):
            raise MarketDataError(
                ErrorCode.UPSTREAM_INVALID_RESPONSE,
                f"AKShare Sina returned an invalid stock response for {symbol.canonical}",
            )
        return frame

    @bounded_upstream(timeout=12)
    def etf_daily_bars(self, symbol: CanonicalSymbol) -> pd.DataFrame:
        try:
            frame = ak.fund_etf_hist_sina(symbol=symbol.sina_code)
        except Exception as exc:
            raise MarketDataError(
                ErrorCode.UPSTREAM_UNAVAILABLE,
                f"AKShare Sina ETF query failed for {symbol.canonical}",
            ) from exc
        if not isinstance(frame, pd.DataFrame):
            raise MarketDataError(
                ErrorCode.UPSTREAM_INVALID_RESPONSE,
                f"AKShare Sina returned an invalid response for {symbol.canonical}",
            )
        return frame

    @bounded_upstream(timeout=12)
    def index_catalog(self) -> list[CatalogInstrument]:
        try:
            frame = ak.stock_zh_index_spot_sina()
        except Exception as exc:
            raise MarketDataError(
                ErrorCode.UPSTREAM_UNAVAILABLE,
                "AKShare Sina index catalog query failed",
            ) from exc
        if not isinstance(frame, pd.DataFrame) or len(frame.columns) < 2:
            raise MarketDataError(
                ErrorCode.UPSTREAM_INVALID_RESPONSE,
                "AKShare Sina returned an invalid index catalog response",
            )
        instruments: list[CatalogInstrument] = []
        for values in frame.iloc[:, :2].itertuples(index=False, name=None):
            try:
                symbol = CanonicalSymbol.from_sina_code(str(values[0]))
            except MarketDataError:
                continue
            instruments.append(
                CatalogInstrument(
                    symbol=symbol,
                    metadata=RawInstrumentMetadata(
                        name=str(values[1]).strip(),
                        list_date="",
                        instrument_type="2",
                        status="1",
                    ),
                )
            )
        return instruments

    @bounded_upstream(timeout=12)
    def index_daily_bars(self, symbol: CanonicalSymbol) -> pd.DataFrame:
        try:
            frame = ak.stock_zh_index_daily(symbol=symbol.sina_code)
        except Exception as exc:
            raise MarketDataError(
                ErrorCode.UPSTREAM_UNAVAILABLE,
                f"AKShare Sina index query failed for {symbol.canonical}",
            ) from exc
        if not isinstance(frame, pd.DataFrame):
            raise MarketDataError(
                ErrorCode.UPSTREAM_INVALID_RESPONSE,
                f"AKShare Sina returned an invalid index response for {symbol.canonical}",
            )
        return frame
