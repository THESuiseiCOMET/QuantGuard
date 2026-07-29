import pytest

from app.errors import ErrorCode, MarketDataError
from app.symbols import CanonicalSymbol


def test_symbol_conversions() -> None:
    sh = CanonicalSymbol.parse("510300.sh")
    sz = CanonicalSymbol.parse("159919.SZ")

    assert sh.canonical == "510300.SH"
    assert sh.baostock_code == "sh.510300"
    assert sh.sina_code == "sh510300"
    assert sh.exchange == "SSE"
    assert sz.baostock_code == "sz.159919"
    assert sz.sina_code == "sz159919"
    assert sz.exchange == "SZSE"
    assert CanonicalSymbol.from_baostock_code("sh.000688").canonical == "000688.SH"
    assert CanonicalSymbol.from_sina_code("sz000688").canonical == "000688.SZ"


def test_invalid_symbol_is_rejected() -> None:
    with pytest.raises(MarketDataError) as error:
        CanonicalSymbol.parse("1B0688")

    assert error.value.code == ErrorCode.INVALID_SYMBOL
