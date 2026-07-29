package com.vanyiung.quantguard.marketdata.provider;

import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;

import java.time.LocalDate;
import java.util.List;

public interface MarketDataProvider {

    default List<ProviderInstrumentMetadata> searchInstruments(String query, int limit) {
        return List.of();
    }

    List<ProviderInstrumentMetadata> lookupInstruments(List<MarketSymbol> symbols);

    List<ProviderDailyBar> fetchDailyBars(List<MarketSymbol> symbols, LocalDate startDate, LocalDate endDate);

    String providerName();
}
