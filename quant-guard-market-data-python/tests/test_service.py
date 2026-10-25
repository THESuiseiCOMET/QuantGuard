from datetime import date

import pandas as pd

from app.clients import CatalogInstrument, RawInstrumentMetadata
from app.errors import ErrorCode, MarketDataError
from app.service import MarketDataService
from app.symbols import CanonicalSymbol


class FakeBaoStock:
    def __init__(
        self,
        metadata: dict[str, RawInstrumentMetadata],
        frames: dict[str, pd.DataFrame] | None = None,
        failures: dict[str, MarketDataError] | None = None,
        daily_failures: dict[str, MarketDataError] | None = None,
    ) -> None:
        self.metadata = metadata
        self.frames = frames or {}
        self.failures = failures or {}
        self.daily_failures = daily_failures or {}
        self.lookup_calls = 0

    def lookup_instrument(self, symbol):
        self.lookup_calls += 1
        if symbol.canonical in self.failures:
            raise self.failures[symbol.canonical]
        if symbol.canonical not in self.metadata:
            raise MarketDataError(
                ErrorCode.INSTRUMENT_NOT_FOUND,
                f"Instrument not found: {symbol.canonical}",
            )
        return self.metadata[symbol.canonical]

    def search_instruments(self, _query):
        return [
            CatalogInstrument(CanonicalSymbol.parse(symbol), item)
            for symbol, item in self.metadata.items()
        ]

    def daily_bars(self, symbol, _start_date, _end_date):
        if symbol.canonical in self.daily_failures:
            raise self.daily_failures[symbol.canonical]
        return self.frames.get(symbol.canonical, pd.DataFrame())


class FakeAkShare:
    def __init__(
        self,
        frames: dict[str, pd.DataFrame] | None = None,
        stocks: list[CatalogInstrument] | None = None,
        indices: list[CatalogInstrument] | None = None,
    ) -> None:
        self.frames = frames or {}
        self.stocks = stocks or []
        self.indices = indices or []

    def stock_catalog(self):
        return self.stocks

    def stock_daily_bars(self, symbol):
        return self.frames.get(symbol.canonical, pd.DataFrame())

    def etf_daily_bars(self, symbol):
        return self.frames.get(symbol.canonical, pd.DataFrame())

    def index_catalog(self):
        return self.indices

    def index_daily_bars(self, symbol):
        return self.frames.get(symbol.canonical, pd.DataFrame())


def metadata(instrument_type: str, name: str = "Test") -> RawInstrumentMetadata:
    return RawInstrumentMetadata(
        name=name,
        list_date="2020-01-02",
        instrument_type=instrument_type,
        status="1",
    )


def test_maps_stock_etf_index_and_unknown_types() -> None:
    service = MarketDataService(
        FakeBaoStock(
            {
                "600519.SH": metadata("1", "Kweichow Moutai"),
                "510300.SH": metadata("5", "CSI 300 ETF"),
                "000001.SH": metadata("2", "SSE Composite Index"),
                "000001.SZ": metadata("9", "Unknown"),
            }
        ),
        FakeAkShare(),
    )

    response = service.lookup_instruments(
        ["600519.SH", "510300.SH", "000001.SH", "000001.SZ"]
    )

    assert response.status == "SUCCESS"
    assert [item.instrument.instrument_type for item in response.results] == [
        "STOCK",
        "ETF",
        "INDEX",
        "UNKNOWN",
    ]


def test_catalog_search_matches_code_and_name_with_exchange_disambiguation() -> None:
    index = CatalogInstrument(
        CanonicalSymbol.parse("000688.SH"),
        metadata("2", "STAR 50"),
    )
    service = MarketDataService(
        FakeBaoStock({"000688.SZ": metadata("1", "Guocheng Mining")}),
        FakeAkShare(indices=[index]),
    )

    by_code = service.search_instruments("000688", 20)
    by_name = service.search_instruments("STAR", 20)

    assert [item.symbol for item in by_code.results] == ["000688.SH", "000688.SZ"]
    assert [item.instrument_type for item in by_code.results] == ["INDEX", "STOCK"]
    assert [item.symbol for item in by_name.results] == ["000688.SH"]


def test_lookup_reuses_warm_catalog_without_repeating_provider_lookup() -> None:
    baostock = FakeBaoStock({"600089.SH": metadata("1", "TBEA")})
    service = MarketDataService(baostock, FakeAkShare())

    search = service.search_instruments("TBEA", 20)
    lookup = service.lookup_instruments(["600089.SH"])

    assert [item.symbol for item in search.results] == ["600089.SH"]
    assert lookup.results[0].instrument.name == "TBEA"
    assert baostock.lookup_calls == 0


def test_stock_volume_is_divided_by_100_and_amount_stays_in_yuan() -> None:
    frame = pd.DataFrame(
        [
            {
                "date": "2026-07-24",
                "open": "10.1",
                "high": "10.8",
                "low": "10.0",
                "close": "10.5",
                "preclose": "10",
                "volume": "123450",
                "amount": "987654.32",
            }
        ]
    )
    service = MarketDataService(
        FakeBaoStock({"600519.SH": metadata("1")}, {"600519.SH": frame}),
        FakeAkShare(),
    )

    response = service.daily_bars(
        ["600519.SH"],
        date(2026, 7, 1),
        date(2026, 7, 31),
    )
    bar = response.results[0].bars[0]

    assert bar.volume_lots == "1234.5"
    assert bar.amount_cny == "987654.32"
    assert bar.previous_close == "10"
    assert bar.change == "0.5"
    assert bar.percentage_change == "5"


def test_etf_sorts_deduplicates_and_computes_before_filtering() -> None:
    frame = pd.DataFrame(
        [
            {
                "date": "2026-01-03",
                "open": "11",
                "high": "12",
                "low": "10",
                "close": "11",
                "volume": "300",
                "amount": "3000",
            },
            {
                "date": "2026-01-01",
                "open": "9",
                "high": "10",
                "low": "8",
                "close": "10",
                "volume": "100",
                "amount": "1000",
            },
            {
                "date": "2026-01-02",
                "open": "10",
                "high": "11",
                "low": "9",
                "close": "10.5",
                "volume": "200",
                "amount": "2000",
            },
            {
                "date": "2026-01-02",
                "open": "10",
                "high": "11",
                "low": "9",
                "close": "10.8",
                "volume": "220",
                "amount": "2200",
            },
        ]
    )
    service = MarketDataService(
        FakeBaoStock({"510300.SH": metadata("5")}),
        FakeAkShare({"510300.SH": frame}),
    )

    response = service.daily_bars(
        ["510300.SH"],
        date(2026, 1, 2),
        date(2026, 1, 3),
    )
    bars = response.results[0].bars

    assert [bar.trade_date for bar in bars] == [date(2026, 1, 2), date(2026, 1, 3)]
    assert bars[0].close == "10.8"
    assert bars[0].previous_close == "10"
    assert bars[0].change == "0.8"
    assert bars[0].percentage_change == "8"
    assert bars[0].volume_lots == "2.2"
    assert bars[0].amount_cny == "2200"
    assert bars[1].previous_close == "10.8"


def test_etf_first_listing_day_has_null_previous_close() -> None:
    frame = pd.DataFrame(
        [
            {
                "date": "2020-11-16",
                "open": "1",
                "high": "1.1",
                "low": "0.9",
                "close": "1",
                "volume": "100",
                "amount": "100",
            }
        ]
    )
    service = MarketDataService(
        FakeBaoStock({"588000.SH": metadata("5")}),
        FakeAkShare({"588000.SH": frame}),
    )

    bar = service.daily_bars(
        ["588000.SH"],
        date(2020, 11, 16),
        date(2020, 11, 16),
    ).results[0].bars[0]

    assert bar.previous_close is None
    assert bar.change is None
    assert bar.percentage_change is None


def test_index_uses_sina_history_and_zero_when_amount_is_unavailable() -> None:
    frame = pd.DataFrame(
        [
            {
                "date": "2020-01-02",
                "open": "1005.617",
                "high": "1019.91",
                "low": "1005.617",
                "close": "1019.723",
                "volume": "165906987",
            },
            {
                "date": "2020-01-03",
                "open": "1020",
                "high": "1030",
                "low": "1010",
                "close": "1025",
                "volume": "200000000",
            },
        ]
    )
    index = CatalogInstrument(
        CanonicalSymbol.parse("000688.SH"),
        metadata("2", "STAR 50"),
    )
    service = MarketDataService(
        FakeBaoStock({}),
        FakeAkShare({"000688.SH": frame}, indices=[index]),
    )

    response = service.daily_bars(
        ["000688.SH"],
        date(2020, 1, 3),
        date(2020, 1, 3),
    )
    bar = response.results[0].bars[0]

    assert bar.previous_close == "1019.723"
    assert bar.volume_lots == "2000000"
    assert bar.amount_cny == "0"


def test_stock_falls_back_to_sina_and_computes_previous_close_before_filtering() -> None:
    frame = pd.DataFrame(
        [
            {
                "date": "2026-07-01",
                "open": "9.8",
                "high": "10.2",
                "low": "9.7",
                "close": "10",
                "volume": "10000",
                "amount": "100000",
            },
            {
                "date": "2026-07-02",
                "open": "10",
                "high": "11",
                "low": "9.9",
                "close": "10.5",
                "volume": "123450",
                "amount": "987654.32",
            },
        ]
    )
    service = MarketDataService(
        FakeBaoStock(
            {"002714.SZ": metadata("1", "Muyuan Foods")},
            daily_failures={
                "002714.SZ": MarketDataError(
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    "BaoStock unavailable",
                )
            },
        ),
        FakeAkShare({"002714.SZ": frame}),
    )

    response = service.daily_bars(
        ["002714.SZ"],
        date(2026, 7, 2),
        date(2026, 7, 2),
    )
    bar = response.results[0].bars[0]

    assert response.status == "SUCCESS"
    assert bar.previous_close == "10"
    assert bar.change == "0.5"
    assert bar.percentage_change == "5"
    assert bar.volume_lots == "1234.5"
    assert bar.amount_cny == "987654.32"


def test_stock_metadata_falls_back_to_akshare_catalog() -> None:
    stock = CatalogInstrument(
        CanonicalSymbol.parse("002714.SZ"),
        metadata("1", "Muyuan Foods"),
    )
    service = MarketDataService(
        FakeBaoStock(
            {},
            failures={
                "002714.SZ": MarketDataError(
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    "BaoStock unavailable",
                )
            },
        ),
        FakeAkShare(stocks=[stock]),
    )

    result = service.lookup_instruments(["002714.SZ"]).results[0]

    assert result.error is None
    assert result.instrument.name == "Muyuan Foods"
    assert result.instrument.instrument_type == "STOCK"


def test_one_symbol_failure_does_not_block_other_symbols() -> None:
    frame = pd.DataFrame(
        [
            {
                "date": "2026-01-01",
                "open": "10",
                "high": "10",
                "low": "10",
                "close": "10",
                "preclose": "9",
                "volume": "100",
                "amount": "1000",
            }
        ]
    )
    service = MarketDataService(
        FakeBaoStock(
            {"600519.SH": metadata("1")},
            {"600519.SH": frame},
            {
                "000001.SZ": MarketDataError(
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    "BaoStock unavailable",
                )
            },
        ),
        FakeAkShare(),
    )

    response = service.daily_bars(
        ["000001.SZ", "600519.SH"],
        date(2026, 1, 1),
        date(2026, 1, 2),
    )

    assert response.status == "PARTIAL_FAILURE"
    assert response.results[0].error.code == ErrorCode.UPSTREAM_UNAVAILABLE
    assert len(response.results[1].bars) == 1


def test_empty_data_invalid_symbol_and_upstream_failure_are_structured() -> None:
    service = MarketDataService(
        FakeBaoStock(
            {"600519.SH": metadata("1")},
            failures={
                "000001.SZ": MarketDataError(
                    ErrorCode.UPSTREAM_INVALID_RESPONSE,
                    "Invalid upstream payload",
                )
            },
        ),
        FakeAkShare(),
    )

    response = service.daily_bars(
        ["BAD", "600519.SH", "000001.SZ"],
        date(2026, 1, 1),
        date(2026, 1, 2),
    )

    assert response.status == "FAILED"
    assert [item.error.code for item in response.results] == [
        ErrorCode.INVALID_SYMBOL,
        ErrorCode.NO_DATA,
        ErrorCode.UPSTREAM_INVALID_RESPONSE,
    ]


def test_unknown_type_cannot_fetch_daily_bars() -> None:
    service = MarketDataService(
        FakeBaoStock({"000001.SZ": metadata("9")}),
        FakeAkShare(),
    )

    response = service.daily_bars(
        ["000001.SZ"],
        date(2026, 1, 1),
        date(2026, 1, 2),
    )

    assert response.results[0].error.code == ErrorCode.UNSUPPORTED_INSTRUMENT_TYPE


def test_successful_primary_catalog_does_not_fetch_redundant_stock_fallback() -> None:
    class SlowFallback(FakeAkShare):
        def stock_catalog(self):
            raise AssertionError("Primary stocks are present: do not call the slow fallback")

    primary = metadata("1", "Primary name")
    duplicate = CatalogInstrument(CanonicalSymbol.parse("600519.SH"), metadata("1", "Fallback name"))
    service = MarketDataService(FakeBaoStock({"600519.SH": primary}), SlowFallback(indices=[duplicate]))
    result = service.search_instruments("600519", 20).results[0]
    assert result.name == "Primary name"
    assert result.list_date == date(2020, 1, 2)


def test_etf_missing_or_empty_amount_does_not_discard_valid_bars() -> None:
    for amount in [{}, {"amount": None}, {"amount": ""}, {"amount": "123.45"}]:
        frame = pd.DataFrame([dict(date="2026-01-02", open="1", high="2", low="1", close="2", volume="100", **amount)])
        service = MarketDataService(FakeBaoStock({"510300.SH": metadata("5")}), FakeAkShare({"510300.SH": frame}))
        bar = service.daily_bars(["510300.SH"], date(2026, 1, 2), date(2026, 1, 2)).results[0].bars[0]
        assert bar.amount_cny == ("123.45" if amount.get("amount") else "0")
        assert bar.volume_lots == "1"


def test_stock_fallback_handles_timeout_empty_and_malformed_primary() -> None:
    fallback = pd.DataFrame([dict(date="2026-01-02", open="1", high="2", low="1", close="2", volume="100", amount="200")])
    for primary in [pd.DataFrame(), pd.DataFrame([{"date": "2026-01-02"}]),
                    pd.DataFrame([dict(date="2026-01-02", open="bad", high="2", low="1", close="2", preclose="1", volume="100", amount="200")])]:
        service = MarketDataService(FakeBaoStock({"600519.SH": metadata("1")}, {"600519.SH": primary}), FakeAkShare({"600519.SH": fallback}))
        assert service.daily_bars(["600519.SH"], date(2026, 1, 2), date(2026, 1, 2)).status == "SUCCESS"
    service = MarketDataService(FakeBaoStock({"600519.SH": metadata("1")}, daily_failures={"600519.SH": MarketDataError(ErrorCode.TIMEOUT, "timed out")}), FakeAkShare({"600519.SH": fallback}))
    assert service.daily_bars(["600519.SH"], date(2026, 1, 2), date(2026, 1, 2)).status == "SUCCESS"


def test_baostock_rows_outside_requested_range_are_not_returned() -> None:
    frame = pd.DataFrame([dict(date=day, open="1", high="2", low="1", close="2", preclose="1", volume="100", amount="200") for day in ["2026-01-01", "2026-01-02", "2026-01-03"]])
    service = MarketDataService(FakeBaoStock({"600519.SH": metadata("1")}, {"600519.SH": frame}), FakeAkShare())
    bars = service.daily_bars(["600519.SH"], date(2026, 1, 2), date(2026, 1, 2)).results[0].bars
    assert [bar.trade_date for bar in bars] == [date(2026, 1, 2)]


def test_partial_numeric_code_does_not_download_catalog() -> None:
    class NoQueries:
        def __getattr__(self, name):
            raise AssertionError("Incomplete code must not start a network request")
    service = MarketDataService(NoQueries(), NoQueries())
    assert service.search_instruments("600", 20).results == []


def test_invalid_date_is_a_structured_error() -> None:
    frame = pd.DataFrame([dict(date=pd.NaT, open="1", high="2", low="1", close="2", volume="100")])
    service = MarketDataService(FakeBaoStock({"510300.SH": metadata("5")}), FakeAkShare({"510300.SH": frame}))
    result = service.daily_bars(["510300.SH"], date(2026, 1, 1), date(2026, 1, 2)).results[0]
    assert result.error.code == ErrorCode.UPSTREAM_INVALID_RESPONSE
