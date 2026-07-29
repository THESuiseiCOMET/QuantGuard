import os
from datetime import date

import pytest

from app.service import MarketDataService


pytestmark = [
    pytest.mark.live,
    pytest.mark.skipif(
        os.getenv("RUN_MARKET_DATA_SMOKE") != "1",
        reason="Set RUN_MARKET_DATA_SMOKE=1 to call real upstream providers",
    ),
]


@pytest.mark.parametrize(
    "symbol",
    ["600519.SH", "000001.SZ", "510300.SH", "588000.SH"],
)
def test_real_network_smoke(symbol: str) -> None:
    service = MarketDataService()

    metadata = service.lookup_instruments([symbol])
    bars = service.daily_bars([symbol], date(2026, 7, 1), date(2026, 7, 25))

    assert metadata.results[0].instrument is not None
    assert bars.results[0].bars
