package com.vanyiung.quantguard.marketdata.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TushareMarketDataProviderTest {

    @Test
    void mapsStockBasicByFieldNameWhenFieldsAreShuffled() throws Exception {
        TushareHttpClient client = mock(TushareHttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        TushareResponse response = new TushareResponse(
                mapper.readTree("[\"name\",\"ts_code\",\"list_date\",\"list_status\",\"curr_type\",\"exchange\",\"symbol\"]"),
                mapper.readTree("[[\"贵州茅台\",\"600519.SH\",\"20010827\",\"L\",\"CNY\",\"SSE\",\"600519\"]]")
        );
        when(client.post(eq("stock_basic"), any(Map.class), any())).thenReturn(response);

        TushareMarketDataProvider provider = new TushareMarketDataProvider(client);
        ProviderInstrumentMetadata metadata = provider.lookupInstruments(List.of(new MarketSymbol("600519", Exchange.SSE))).getFirst();

        assertEquals("600519.SH", metadata.marketSymbol().canonicalCode());
        assertEquals("贵州茅台", metadata.instrumentName());
        assertEquals(Exchange.SSE, metadata.exchange());
        assertEquals(LocalDate.of(2001, 8, 27), metadata.listDate());
    }

    @Test
    void mapsDailyBarsByFieldNameAndConvertsAmountToCnyYuan() throws Exception {
        TushareHttpClient client = mock(TushareHttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        TushareResponse response = new TushareResponse(
                mapper.readTree("[\"amount\",\"vol\",\"pct_chg\",\"change\",\"pre_close\",\"close\",\"low\",\"high\",\"open\",\"trade_date\",\"ts_code\"]"),
                mapper.readTree("[[\"123.45\",\"10.5\",\"1.23\",\"2.00\",\"100.00\",\"102.00\",\"99.00\",\"103.00\",\"101.00\",\"20260724\",\"000001.SZ\"]]")
        );
        when(client.post(eq("daily"), any(Map.class), any())).thenReturn(response);

        TushareMarketDataProvider provider = new TushareMarketDataProvider(client);
        ProviderDailyBar bar = provider.fetchDailyBars(
                List.of(new MarketSymbol("000001", Exchange.SZSE)),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 24)).getFirst();

        assertEquals("000001.SZ", bar.marketSymbol().canonicalCode());
        assertEquals(LocalDate.of(2026, 7, 24), bar.tradeDate());
        assertEquals(new BigDecimal("123450.00"), bar.amountCny());
        assertEquals(new BigDecimal("10.5"), bar.volumeLots());
    }

    @Test
    void rejectsMissingFieldAndProviderErrorCode() throws Exception {
        TushareHttpClient missingFieldClient = mock(TushareHttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        when(missingFieldClient.post(eq("daily"), any(Map.class), any())).thenReturn(new TushareResponse(
                mapper.readTree("[\"ts_code\"]"),
                mapper.readTree("[]")
        ));
        TushareMarketDataProvider provider = new TushareMarketDataProvider(missingFieldClient);
        assertThrows(MarketDataProviderException.class, () -> provider.fetchDailyBars(
                List.of(new MarketSymbol("000001", Exchange.SZSE)),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 24)));

        TushareHttpClient client = new TushareHttpClient(new MarketDataProperties(), mapper);
        assertThrows(MarketDataProviderException.class, () -> client.parse("""
                {"code":2002,"msg":"权限不足","data":{"fields":[],"items":[]}}
                """));
    }
}
