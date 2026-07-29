package com.vanyiung.quantguard.marketdata.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PythonMarketDataProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsCatalogSearchResultsIncludingIndex() throws Exception {
        PythonMarketDataHttpClient client = mock(PythonMarketDataHttpClient.class);
        when(client.post(eq("/v1/instruments/search"), eq(Map.of(
                "query", "000688",
                "limit", 20
        )))).thenReturn(objectMapper.readTree("""
                {
                  "results":[{
                    "symbol":"000688.SH",
                    "name":"科创50",
                    "exchange":"SSE",
                    "currency":"CNY",
                    "list_status":"L",
                    "list_date":null,
                    "instrument_type":"INDEX"
                  }]
                }
                """));
        PythonMarketDataProvider provider = new PythonMarketDataProvider(client);

        ProviderInstrumentMetadata result = provider.searchInstruments("000688", 20).getFirst();

        assertEquals("000688.SH", result.marketSymbol().canonicalCode());
        assertEquals("科创50", result.instrumentName());
        assertEquals(ProviderInstrumentType.INDEX, result.instrumentType());
    }

    @Test
    void mapsEtfMetadataAndDecimalStringDailyBars() throws Exception {
        PythonMarketDataHttpClient client = mock(PythonMarketDataHttpClient.class);
        when(client.post(eq("/v1/instruments/lookup"), eq(Map.of("symbols", List.of("510300.SH")))))
                .thenReturn(objectMapper.readTree("""
                        {
                          "status":"SUCCESS",
                          "results":[{
                            "symbol":"510300.SH",
                            "instrument":{
                              "symbol":"510300.SH",
                              "name":"沪深300ETF",
                              "exchange":"SSE",
                              "currency":"CNY",
                              "list_status":"L",
                              "list_date":"2012-05-28",
                              "instrument_type":"ETF"
                            },
                            "error":null
                          }]
                        }
                        """));
        when(client.post(eq("/v1/daily-bars"), eq(Map.of(
                "symbols", List.of("510300.SH"),
                "start_date", "2012-05-28",
                "end_date", "2012-05-28"
        )))).thenReturn(objectMapper.readTree("""
                {
                  "status":"SUCCESS",
                  "results":[{
                    "symbol":"510300.SH",
                    "bars":[{
                      "symbol":"510300.SH",
                      "trade_date":"2012-05-28",
                      "open":"2.551",
                      "high":"2.607",
                      "low":"2.544",
                      "close":"2.604",
                      "previous_close":null,
                      "change":null,
                      "percentage_change":null,
                      "volume_lots":"12775187.2",
                      "amount_cny":"3285755392"
                    }],
                    "error":null
                  }]
                }
                """));
        PythonMarketDataProvider provider = new PythonMarketDataProvider(client);

        ProviderInstrumentMetadata metadata = provider.lookupInstruments(
                List.of(new MarketSymbol("510300", Exchange.SSE))).getFirst();
        ProviderDailyBar bar = provider.fetchDailyBars(
                List.of(new MarketSymbol("510300", Exchange.SSE)),
                LocalDate.of(2012, 5, 28),
                LocalDate.of(2012, 5, 28)).getFirst();

        assertEquals(ProviderInstrumentType.ETF, metadata.instrumentType());
        assertEquals("沪深300ETF", metadata.instrumentName());
        assertEquals(LocalDate.of(2012, 5, 28), metadata.listDate());
        assertEquals(new BigDecimal("12775187.2"), bar.volumeLots());
        assertEquals(new BigDecimal("3285755392"), bar.amountCny());
        assertNull(bar.previousClose());
    }

    @Test
    void mapsStructuredProviderError() throws Exception {
        PythonMarketDataHttpClient client = mock(PythonMarketDataHttpClient.class);
        when(client.post(eq("/v1/daily-bars"), eq(Map.of(
                "symbols", List.of("588000.SH"),
                "start_date", "2020-11-16",
                "end_date", "2020-11-16"
        )))).thenReturn(objectMapper.readTree("""
                {
                  "status":"FAILED",
                  "results":[{
                    "symbol":"588000.SH",
                    "bars":[],
                    "error":{"code":"NO_DATA","message":"No daily bars returned"}
                  }]
                }
                """));
        PythonMarketDataProvider provider = new PythonMarketDataProvider(client);

        MarketDataProviderException error = assertThrows(
                MarketDataProviderException.class,
                () -> provider.fetchDailyBars(
                        List.of(new MarketSymbol("588000", Exchange.SSE)),
                        LocalDate.of(2020, 11, 16),
                        LocalDate.of(2020, 11, 16)));

        assertEquals(MarketDataProviderErrorType.NO_DATA, error.errorType());
    }

    @Test
    void rejectsInvalidResponse() throws Exception {
        PythonMarketDataHttpClient client = mock(PythonMarketDataHttpClient.class);
        when(client.post(eq("/v1/instruments/lookup"), eq(Map.of("symbols", List.of("600519.SH")))))
                .thenReturn(objectMapper.readTree("""
                        {"status":"SUCCESS","results":"not-an-array"}
                        """));
        PythonMarketDataProvider provider = new PythonMarketDataProvider(client);

        MarketDataProviderException error = assertThrows(
                MarketDataProviderException.class,
                () -> provider.lookupInstruments(List.of(new MarketSymbol("600519", Exchange.SSE))));

        assertEquals(MarketDataProviderErrorType.UPSTREAM_INVALID_RESPONSE, error.errorType());
    }
}
