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
        TushareMarketDataProvider tushare = mock(TushareMarketDataProvider.class);
        PythonMarketDataProvider python = mock(PythonMarketDataProvider.class);
        when(python.providerName()).thenReturn("PYTHON");
        ConfiguredMarketDataProvider provider = new ConfiguredMarketDataProvider(properties, tushare, python);

        assertEquals("PYTHON", provider.providerName());
        provider.lookupInstruments(List.of());

        verify(python).lookupInstruments(List.of());
    }

    @Test
    void selectsTushareProviderWithoutChangingExistingImplementation() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setProvider("tushare");
        TushareMarketDataProvider tushare = mock(TushareMarketDataProvider.class);
        PythonMarketDataProvider python = mock(PythonMarketDataProvider.class);
        ConfiguredMarketDataProvider provider = new ConfiguredMarketDataProvider(properties, tushare, python);

        provider.fetchDailyBars(List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));

        verify(tushare).fetchDailyBars(List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
    }

    @Test
    void unsupportedProviderFailsOnUseInsteadOfApplicationStartup() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setProvider("auto");
        ConfiguredMarketDataProvider provider = new ConfiguredMarketDataProvider(
                properties,
                mock(TushareMarketDataProvider.class),
                mock(PythonMarketDataProvider.class));

        MarketDataProviderException error = assertThrows(
                MarketDataProviderException.class,
                provider::providerName);

        assertEquals(MarketDataProviderErrorType.NOT_CONFIGURED, error.errorType());
    }
}
