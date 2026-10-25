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
    monkeypatch.setattr(
        "app.clients.bs.query_stock_basic",
        lambda **_kwargs: (_ for _ in ()).throw(RuntimeError("upstream failed")),
    )
    monkeypatch.setattr("app.clients.bs.logout", lambda: logout_calls.append(True))

    with pytest.raises(MarketDataError) as error:
        BaoStockClient(isolated=False).lookup_instrument(CanonicalSymbol.parse("600519.SH"))

    assert error.value.code == ErrorCode.UPSTREAM_UNAVAILABLE
    assert logout_calls == [True]


def test_baostock_login_timeout_is_structured_without_logout(monkeypatch) -> None:
    logout_calls = []
    monkeypatch.setattr("app.clients.bs.login", lambda: (_ for _ in ()).throw(TimeoutError("unreachable")))
    monkeypatch.setattr("app.clients.bs.logout", lambda: logout_calls.append(True))
    with pytest.raises(MarketDataError) as error:
        BaoStockClient(isolated=False).lookup_instrument(CanonicalSymbol.parse("600519.SH"))
    assert error.value.code == ErrorCode.TIMEOUT
    assert logout_calls == []
