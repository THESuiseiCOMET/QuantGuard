package com.vanyiung.quantguard.marketdata.provider;

import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Component
@Primary
public class ConfiguredMarketDataProvider implements MarketDataProvider {

    private final MarketDataProperties properties;
    private final PythonMarketDataProvider pythonProvider;

    public ConfiguredMarketDataProvider(MarketDataProperties properties,
                                        PythonMarketDataProvider pythonProvider) {
        this.properties = properties;
        this.pythonProvider = pythonProvider;
    }

    @Override
    public List<ProviderInstrumentMetadata> searchInstruments(String query, int limit) {
        return delegate().searchInstruments(query, limit);
    }

    @Override
    public List<ProviderInstrumentMetadata> lookupInstruments(List<MarketSymbol> symbols) {
        return delegate().lookupInstruments(symbols);
    }

    @Override
    public List<ProviderDailyBar> fetchDailyBars(List<MarketSymbol> symbols,
                                                 LocalDate startDate,
                                                 LocalDate endDate) {
        return delegate().fetchDailyBars(symbols, startDate, endDate);
    }

    @Override
    public String providerName() {
        return delegate().providerName();
    }

    private MarketDataProvider delegate() {
        String provider = properties.getProvider() == null
                ? ""
                : properties.getProvider().trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "python" -> pythonProvider;
            default -> throw new MarketDataProviderException(MarketDataProviderErrorType.NOT_CONFIGURED,
                    "Unsupported market data provider: " + properties.getProvider());
        };
    }
}
