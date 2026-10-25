import pytest

from app.clients import BaoStockClient
from app.errors import ErrorCode, MarketDataError
from app.symbols import CanonicalSymbol


class SuccessfulLogin:
    error_code = "0"
    error_msg = "success"


def test_baostock_logout_runs_when_query_fails(monkeypatch) -> None:
    logout_calls: list[bool] = []

    monkeypatch.setattr("app.clients.bs.login", lambda: SuccessfulLogin())
    monkeypatch.setattr(BaoStockClient, "_ensure_available", lambda _self: None)
    monkeypatch.setattr(
        "app.clients.bs.query_stock_basic",
        lambda **_kwargs: (_ for _ in ()).throw(RuntimeError("upstream failed")),
    )
    monkeypatch.setattr("app.clients.bs.logout", lambda: logout_calls.append(True))

    with pytest.raises(MarketDataError) as error:
        BaoStockClient(isolated=False).lookup_instrument(CanonicalSymbol.parse("600519.SH"))

    assert error.value.code == ErrorCode.UPSTREAM_UNAVAILABLE
    assert logout_calls == [True]


def test_baostock_unreachable_fails_before_login(monkeypatch) -> None:
    login_calls: list[bool] = []

    monkeypatch.setattr(
        "app.clients.socket.create_connection",
        lambda *_args, **_kwargs: (_ for _ in ()).throw(TimeoutError("unreachable")),
    )
    monkeypatch.setattr("app.clients.bs.login", lambda: login_calls.append(True))

    with pytest.raises(MarketDataError) as error:
        BaoStockClient(isolated=False).lookup_instrument(CanonicalSymbol.parse("600519.SH"))

    assert error.value.code == ErrorCode.UPSTREAM_UNAVAILABLE
    assert login_calls == []
