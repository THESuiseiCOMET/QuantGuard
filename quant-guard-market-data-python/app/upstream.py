"""Bound blocking vendor SDK calls without leaving hung threads behind."""

from contextlib import redirect_stderr, redirect_stdout
from copy import copy
from functools import wraps
import logging
import multiprocessing as mp
import os
from threading import BoundedSemaphore
from time import monotonic

from app.errors import ErrorCode, MarketDataError


log = logging.getLogger(__name__)
_SLOTS = BoundedSemaphore(2)


def _execute(connection, function, args, kwargs):
    try:
        # Vendor print/progress output is noisy; the parent logs structured failures.
        with open(os.devnull, "w") as sink, redirect_stdout(sink), redirect_stderr(sink):
            value = function(*args, **kwargs)
        connection.send((True, value))
    except MarketDataError as exc:
        connection.send((False, exc.code.value, exc.message))
    except Exception as exc:
        connection.send((False, ErrorCode.UPSTREAM_UNAVAILABLE.value, str(exc)))
    finally:
        connection.close()


def run_bounded(function, args=(), kwargs=None, *, timeout=12):
    """At most two short-lived workers; every exit joins/reaps its worker."""
    label = function.__qualname__
    if not _SLOTS.acquire(timeout=1):
        raise MarketDataError(ErrorCode.TIMEOUT, "Market-data providers are busy; retry shortly")
    context = mp.get_context("spawn")
    receiver, sender = context.Pipe(duplex=False)
    process = context.Process(target=_execute, args=(sender, function, args, kwargs or {}), daemon=True)
    started = monotonic()
    try:
        process.start()
        sender.close()
        if not receiver.poll(max(0, timeout - (monotonic() - started))):
            raise MarketDataError(ErrorCode.TIMEOUT, f"{label} timed out after {timeout}s")
        result = receiver.recv()
        if not result[0]:
            raise MarketDataError(ErrorCode(result[1]), result[2])
        return result[1]
    except MarketDataError as exc:
        log.warning("Upstream call failed: operation=%s code=%s message=%s", label, exc.code, exc.message)
        raise
    except (EOFError, OSError) as exc:
        raise MarketDataError(ErrorCode.UPSTREAM_UNAVAILABLE, f"{label} worker failed") from exc
    finally:
        sender.close()
        receiver.close()
        if process.pid is not None:
            if process.is_alive():
                process.terminate()
            process.join(timeout=2)
            if process.is_alive():
                process.kill()
                process.join(timeout=2)
            process.close()
        _SLOTS.release()


def bounded_upstream(*, timeout=12):
    def decorate(function):
        @wraps(function)
        def call(self, *args, **kwargs):
            if not self._isolated:
                return function(self, *args, **kwargs)
            worker_client = copy(self)
            worker_client._isolated = False
            return run_bounded(getattr(worker_client, function.__name__), args, kwargs, timeout=timeout)
        return call
    return decorate
