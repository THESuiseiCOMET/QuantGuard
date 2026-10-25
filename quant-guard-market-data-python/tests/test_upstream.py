import multiprocessing as mp
import time

import pytest

from app.errors import ErrorCode, MarketDataError
from app.upstream import run_bounded


def test_worker_returns_result_and_is_reaped():
    before = {child.pid for child in mp.active_children()}
    assert run_bounded(sum, ([1, 2, 3],), timeout=5) == 6
    assert {child.pid for child in mp.active_children()} == before


def test_hung_call_times_out_without_leaking_child():
    before = {child.pid for child in mp.active_children()}
    started = time.monotonic()
    with pytest.raises(MarketDataError) as error:
        run_bounded(time.sleep, (10,), timeout=0.3)
    assert error.value.code == ErrorCode.TIMEOUT
    assert time.monotonic() - started < 5
    assert {child.pid for child in mp.active_children()} == before


def test_worker_failure_is_structured():
    with pytest.raises(MarketDataError) as error:
        run_bounded(int, ("not an integer",), timeout=5)
    assert error.value.code == ErrorCode.UPSTREAM_UNAVAILABLE
