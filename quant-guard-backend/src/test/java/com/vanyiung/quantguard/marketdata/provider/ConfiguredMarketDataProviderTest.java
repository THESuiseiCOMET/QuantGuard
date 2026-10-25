package com.vanyiung.quantguard.marketdata.provider;

import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguredMarketDataProviderTest {

    @Test
    void selectsPythonProvider() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setProvider("python");
        PythonMarketDataProvider python = mock(PythonMarketDataProvider.class);
        when(python.providerName()).thenReturn("PYTHON");
        ConfiguredMarketDataProvider provider = new ConfiguredMarketDataProvider(properties, python);

        assertEquals("PYTHON", provider.providerName());
        provider.lookupInstruments(List.of());

        verify(python).lookupInstruments(List.of());
    }

    @Test
    void delegatesDailyBarsToPythonProvider() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setProvider(" PYTHON ");
        PythonMarketDataProvider python = mock(PythonMarketDataProvider.class);
        ConfiguredMarketDataProvider provider = new ConfiguredMarketDataProvider(properties, python);

        provider.fetchDailyBars(List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));

        verify(python).fetchDailyBars(List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
    }

    @Test
    void unsupportedProviderFailsOnUseInsteadOfApplicationStartup() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setProvider("auto");
        ConfiguredMarketDataProvider provider = new ConfiguredMarketDataProvider(
                properties,
                mock(PythonMarketDataProvider.class));

        MarketDataProviderException error = assertThrows(
                MarketDataProviderException.class,
                provider::providerName);

        assertEquals(MarketDataProviderErrorType.NOT_CONFIGURED, error.errorType());
    }
}
