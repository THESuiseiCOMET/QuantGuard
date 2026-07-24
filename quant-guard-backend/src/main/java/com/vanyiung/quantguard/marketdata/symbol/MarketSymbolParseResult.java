package com.vanyiung.quantguard.marketdata.symbol;

public record MarketSymbolParseResult(
        String originalInput,
        MarketSymbol symbol,
        boolean valid,
        String message
) {

    public static MarketSymbolParseResult valid(String originalInput, MarketSymbol symbol) {
        return new MarketSymbolParseResult(originalInput, symbol, true, "OK");
    }

    public static MarketSymbolParseResult invalid(String originalInput, String message) {
        return new MarketSymbolParseResult(originalInput, null, false, message);
    }
}
