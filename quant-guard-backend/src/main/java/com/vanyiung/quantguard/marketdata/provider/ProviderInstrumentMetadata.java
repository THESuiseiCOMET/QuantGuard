package com.vanyiung.quantguard.marketdata.provider;

import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;

import java.time.LocalDate;

public record ProviderInstrumentMetadata(
        MarketSymbol marketSymbol,
        String instrumentName,
        Exchange exchange,
        String currency,
        String listStatus,
        LocalDate listDate,
        ProviderInstrumentType instrumentType
) {
}
