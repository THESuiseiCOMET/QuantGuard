from dataclasses import dataclass
from datetime import date
from threading import Lock

import akshare as ak
import baostock as bs
import pandas as pd

from app.errors import ErrorCode, MarketDataError
from app.symbols import CanonicalSymbol


_BAOSTOCK_SESSION_LOCK = Lock()


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
    _DAILY_FIELDS = (
        "date,code,open,high,low,close,preclose,volume,amount,"
        "adjustflag,tradestatus,pctChg"
    )

    def lookup_instrument(self, symbol: CanonicalSymbol) -> RawInstrumentMetadata:
        with _BAOSTOCK_SESSION_LOCK:
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

    def list_instruments(self) -> list[CatalogInstrument]:
        with _BAOSTOCK_SESSION_LOCK:
            login_result = bs.login()
            try:
                self._require_success(login_result, "BaoStock login failed", ErrorCode.UPSTREAM_UNAVAILABLE)
                result = bs.query_stock_basic()
                rows = self._read_rows(result, "BaoStock instrument catalog response is invalid")
                instruments: list[CatalogInstrument] = []
                for values in rows:
                    row = dict(zip(result.fields, values, strict=True))
                    instrument_type = (row.get("type") or "").strip()
                    if instrument_type not in {"1", "2", "5"}:
                        continue
                    try:
                        symbol = CanonicalSymbol.from_baostock_code(row.get("code") or "")
                    except MarketDataError:
                        continue
                    instruments.append(
                        CatalogInstrument(
                            symbol=symbol,
                            metadata=RawInstrumentMetadata(
                                name=(row.get("code_name") or "").strip(),
                                list_date=(row.get("ipoDate") or "").strip(),
                                instrument_type=instrument_type,
                                status=(row.get("status") or "").strip(),
                            ),
                        )
                    )
                return instruments
            except MarketDataError:
                raise
            except Exception as exc:
                raise MarketDataError(
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    "BaoStock instrument catalog query failed",
                ) from exc
            finally:
                bs.logout()

    def daily_bars(
        self,
        symbol: CanonicalSymbol,
        start_date: date,
        end_date: date,
    ) -> pd.DataFrame:
        with _BAOSTOCK_SESSION_LOCK:
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

    def _require_success(self, result: object, message: str, code: ErrorCode) -> None:
        if getattr(result, "error_code", None) != "0":
            detail = getattr(result, "error_msg", "")
            raise MarketDataError(code, f"{message}: {detail}".rstrip(": "))


class AkShareClient:
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
