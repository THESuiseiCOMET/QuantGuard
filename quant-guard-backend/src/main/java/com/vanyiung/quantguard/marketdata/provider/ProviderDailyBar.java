package com.vanyiung.quantguard.marketdata.provider;

import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderDailyBar(
        MarketSymbol marketSymbol,
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal percentageChange,
        BigDecimal volumeLots,
        BigDecimal amountCny
) {
}
