from datetime import date

from fastapi.testclient import TestClient

import app.main as main_module
from app.clients import CatalogInstrument, RawInstrumentMetadata
from app.service import MarketDataService
from app.symbols import CanonicalSymbol


class MetadataOnlyBaoStock:
    def lookup_instrument(self, _symbol):
        return RawInstrumentMetadata("Test Stock", "2020-01-01", "1", "1")

    def daily_bars(self, _symbol, _start_date: date, _end_date: date):
        raise AssertionError("Network client must not be called by this test")

    def search_instruments(self, _query):
        return [
            CatalogInstrument(
                CanonicalSymbol.parse("600519.SH"),
                RawInstrumentMetadata("Test Stock", "2020-01-01", "1", "1"),
            )
        ]


class NoAkShare:
    def stock_catalog(self):
        return []

    def stock_daily_bars(self, _symbol):
        raise AssertionError("Network client must not be called by this test")

    def etf_daily_bars(self, _symbol):
        raise AssertionError("Network client must not be called by this test")

    def index_catalog(self):
        return []


def test_health_and_lookup_endpoint(monkeypatch) -> None:
    monkeypatch.setattr(
        main_module,
        "service",
        MarketDataService(MetadataOnlyBaoStock(), NoAkShare()),
    )
    client = TestClient(main_module.app, raise_server_exceptions=False)

    health = client.get("/health")
    lookup = client.post("/v1/instruments/lookup", json={"symbols": ["600519.SH", "BAD"]})
    search = client.post("/v1/instruments/search", json={"query": "Test", "limit": 10})

    assert health.status_code == 200
    assert health.json()["status"] == "UP"
    assert lookup.status_code == 200
    assert lookup.json()["status"] == "PARTIAL_FAILURE"
    assert lookup.json()["results"][0]["instrument"]["instrument_type"] == "STOCK"
    assert lookup.json()["results"][1]["error"]["code"] == "INVALID_SYMBOL"
    assert search.status_code == 200
    assert search.json()["results"][0]["symbol"] == "600519.SH"


def test_invalid_request_does_not_expose_stack_trace() -> None:
    client = TestClient(main_module.app, raise_server_exceptions=False)

    response = client.post(
        "/v1/daily-bars",
        json={
            "symbols": ["600519.SH"],
            "start_date": "2026-02-01",
            "end_date": "2026-01-01",
        },
    )

    assert response.status_code == 422
    assert response.json() == {
        "code": "INVALID_SYMBOL",
        "message": "Invalid market-data request",
    }
    assert "traceback" not in response.text.lower()


def test_desktop_shutdown_requires_matching_token_and_loopback(monkeypatch) -> None:
    requested = []
    monkeypatch.setenv("QUANTGUARD_DESKTOP_SHUTDOWN_TOKEN", "desktop-secret")
    monkeypatch.setattr(
        main_module.app.state,
        "request_desktop_shutdown",
        lambda: requested.append(True),
        raising=False,
    )
    client = TestClient(
        main_module.app,
        raise_server_exceptions=False,
        client=("127.0.0.1", 51000),
    )

    forbidden = client.post(
        "/internal/desktop/shutdown",
        headers={"X-QuantGuard-Shutdown-Token": "wrong"},
    )
    accepted = client.post(
        "/internal/desktop/shutdown",
        headers={"X-QuantGuard-Shutdown-Token": "desktop-secret"},
    )
    remote_client = TestClient(
        main_module.app,
        raise_server_exceptions=False,
        client=("192.0.2.10", 51000),
    )
    remote = remote_client.post(
        "/internal/desktop/shutdown",
        headers={"X-QuantGuard-Shutdown-Token": "desktop-secret"},
    )

    assert forbidden.status_code == 403
    assert remote.status_code == 403
    assert accepted.status_code == 202
    assert accepted.json() == {"status": "STOPPING"}
    assert requested == [True]


def test_desktop_shutdown_is_hidden_without_token(monkeypatch) -> None:
    monkeypatch.delenv("QUANTGUARD_DESKTOP_SHUTDOWN_TOKEN", raising=False)
    client = TestClient(
        main_module.app,
        raise_server_exceptions=False,
        client=("127.0.0.1", 51000),
    )

    response = client.post("/internal/desktop/shutdown")

    assert response.status_code == 404


def test_search_timeout_preserves_error_code_and_message(monkeypatch) -> None:
    from app.errors import ErrorCode, MarketDataError

    class TimedOut:
        def search_instruments(self, _query, _limit):
            raise MarketDataError(ErrorCode.TIMEOUT, "Catalog source timed out")

    monkeypatch.setattr(main_module, "service", TimedOut())
    response = TestClient(main_module.app).post("/v1/instruments/search", json={"query": "600519"})
    assert response.status_code == 504
    assert response.json() == {"code": "TIMEOUT", "message": "Catalog source timed out"}
