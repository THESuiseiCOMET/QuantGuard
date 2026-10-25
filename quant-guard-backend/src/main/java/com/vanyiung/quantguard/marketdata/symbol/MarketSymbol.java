package com.vanyiung.quantguard.marketdata.symbol;

import com.vanyiung.quantguard.enums.Exchange;

public record MarketSymbol(String symbol, Exchange exchange) {

    public String canonicalCode() {
        return symbol + "." + switch (exchange) {
            case SSE -> "SH";
            case SZSE -> "SZ";
            default -> exchange.name();
        };
    }
}
