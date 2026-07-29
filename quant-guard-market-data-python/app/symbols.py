import re
from dataclasses import dataclass

from app.errors import ErrorCode, MarketDataError


_CANONICAL_PATTERN = re.compile(r"^(?P<code>\d{6})\.(?P<suffix>SH|SZ)$")


@dataclass(frozen=True)
class CanonicalSymbol:
    code: str
    suffix: str

    @classmethod
    def parse(cls, value: str) -> "CanonicalSymbol":
        normalized = (value or "").strip().upper()
        match = _CANONICAL_PATTERN.fullmatch(normalized)
        if match is None:
            raise MarketDataError(
                ErrorCode.INVALID_SYMBOL,
                f"Invalid symbol '{value}'. Expected XXXXXX.SH or XXXXXX.SZ.",
            )
        return cls(match.group("code"), match.group("suffix"))

    @classmethod
    def from_baostock_code(cls, value: str) -> "CanonicalSymbol":
        normalized = (value or "").strip().lower()
        if not re.fullmatch(r"(sh|sz)\.\d{6}", normalized):
            raise MarketDataError(
                ErrorCode.INVALID_SYMBOL,
                f"Invalid BaoStock symbol '{value}'.",
            )
        prefix, code = normalized.split(".", maxsplit=1)
        return cls(code, prefix.upper())

    @classmethod
    def from_sina_code(cls, value: str) -> "CanonicalSymbol":
        normalized = (value or "").strip().lower()
        if not re.fullmatch(r"(sh|sz)\d{6}", normalized):
            raise MarketDataError(
                ErrorCode.INVALID_SYMBOL,
                f"Invalid Sina symbol '{value}'.",
            )
        return cls(normalized[2:], normalized[:2].upper())

    @property
    def canonical(self) -> str:
        return f"{self.code}.{self.suffix}"

    @property
    def exchange(self) -> str:
        return "SSE" if self.suffix == "SH" else "SZSE"

    @property
    def baostock_code(self) -> str:
        return f"{self.suffix.lower()}.{self.code}"

    @property
    def sina_code(self) -> str:
        return f"{self.suffix.lower()}{self.code}"
