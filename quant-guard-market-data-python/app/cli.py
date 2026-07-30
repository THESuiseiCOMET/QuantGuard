import argparse
import logging
from pathlib import Path
from typing import Sequence

import uvicorn

from app import __version__
from app.logging_config import build_logging_config, write_startup_error
from app.main import app


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="QuantGuard local market-data service")
    parser.add_argument("--host", default="127.0.0.1", type=_loopback_host)
    parser.add_argument("--port", default=8001, type=_port)
    parser.add_argument("--log-dir", type=Path)
    parser.add_argument("--version", action="version", version=__version__)
    return parser


def main(arguments: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(arguments)
    try:
        log_config = build_logging_config(args.log_dir)
        config = uvicorn.Config(
            app=app,
            host=args.host,
            port=args.port,
            workers=1,
            log_config=log_config,
        )
        server = uvicorn.Server(config)
        app.state.request_desktop_shutdown = lambda: setattr(server, "should_exit", True)
        server.run()
        return 0
    except KeyboardInterrupt:
        return 0
    except Exception as exc:
        logging.getLogger(__name__).exception("Market-data service failed to start")
        write_startup_error(str(exc))
        return 1


def _loopback_host(value: str) -> str:
    normalized = value.strip()
    if normalized == "127.0.0.1":
        return normalized
    raise argparse.ArgumentTypeError("host must be 127.0.0.1")


def _port(value: str) -> int:
    try:
        port = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("port must be an integer") from exc
    if port < 1 or port > 65535:
        raise argparse.ArgumentTypeError("port must be between 1 and 65535")
    return port


if __name__ == "__main__":
    raise SystemExit(main())
