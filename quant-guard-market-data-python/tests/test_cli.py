import json
import logging
import logging.config

import pytest

from app import __version__
from app.cli import build_parser
from app.logging_config import build_logging_config


def test_cli_defaults_to_local_single_service() -> None:
    args = build_parser().parse_args([])

    assert args.host == "127.0.0.1"
    assert args.port == 8001
    assert args.log_dir is None


@pytest.mark.parametrize(
    "host",
    ["0.0.0.0", "192.168.1.10", "example.com", "localhost", "::1"],
)
def test_cli_rejects_non_loopback_hosts(host: str) -> None:
    with pytest.raises(SystemExit):
        build_parser().parse_args(["--host", host])


def test_cli_reports_version(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit) as exception:
        build_parser().parse_args(["--version"])

    assert exception.value.code == 0
    assert capsys.readouterr().out.strip() == __version__


def test_file_logging_is_structured_and_rotating(tmp_path) -> None:
    logging.config.dictConfig(build_logging_config(tmp_path))
    logging.getLogger("uvicorn.error").info("runtime ready")

    log_path = tmp_path / "quantguard-market-data.log"
    payload = json.loads(log_path.read_text(encoding="utf-8").strip())
    assert payload["level"] == "INFO"
    assert payload["message"] == "runtime ready"
