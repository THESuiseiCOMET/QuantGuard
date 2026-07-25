package com.vanyiung.quantguard.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.vanyiung.quantguard.dto.DailyPriceSummaryResponse;
import com.vanyiung.quantguard.dto.DailyPriceSyncRequest;
import com.vanyiung.quantguard.dto.DailyPriceSyncResult;
import com.vanyiung.quantguard.dto.ImportInstrumentsRequest;
import com.vanyiung.quantguard.dto.InstrumentImportPreviewItem;
import com.vanyiung.quantguard.dto.InstrumentImportPreviewRequest;
import com.vanyiung.quantguard.dto.InstrumentImportResult;
import com.vanyiung.quantguard.entity.InstrumentDailyPrice;
import com.vanyiung.quantguard.entity.SecurityInstrument;
import com.vanyiung.quantguard.enums.AssetType;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.enums.InstrumentStatus;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.mapper.InstrumentDailyPriceMapper;
import com.vanyiung.quantguard.mapper.MarketDataSyncLogMapper;
import com.vanyiung.quantguard.mapper.SecurityInstrumentMapper;
import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import com.vanyiung.quantguard.marketdata.provider.MarketDataProvider;
import com.vanyiung.quantguard.marketdata.provider.MarketDataProviderErrorType;
import com.vanyiung.quantguard.marketdata.provider.MarketDataProviderException;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbolParser;
import com.vanyiung.quantguard.service.impl.MarketDataServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceImplTest {

    @Mock
    private SecurityInstrumentMapper securityInstrumentMapper;

    @Mock
    private InstrumentDailyPriceMapper dailyPriceMapper;

    @Mock
    private MarketDataSyncLogMapper syncLogMapper;

    @Mock
    private MarketDataProvider marketDataProvider;

    private MarketDataServiceImpl marketDataService;

    @BeforeEach
    void setUp() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setDefaultStartDate(LocalDate.of(2020, 1, 1));
        marketDataService = new MarketDataServiceImpl(securityInstrumentMapper, dailyPriceMapper,
                syncLogMapper, marketDataProvider, properties, new MarketSymbolParser());
    }

    @Test
    void previewFallsBackToCanonicalCodeWhenProviderMetadataIsUnavailable() {
        when(marketDataProvider.lookupInstruments(any())).thenThrow(permissionDenied());
        when(securityInstrumentMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        List<InstrumentImportPreviewItem> items = marketDataService.previewImport(
                new InstrumentImportPreviewRequest(List.of("600519")));

        assertEquals(1, items.size());
        InstrumentImportPreviewItem item = items.getFirst();
        assertTrue(item.valid());
        assertEquals("600519.SH", item.canonicalCode());
        assertEquals("600519.SH", item.instrumentName());
        assertEquals(Exchange.SSE, item.exchange());
        assertTrue(item.message().contains("using canonical code"));
    }

    @Test
    void importCreatesActiveInstrumentWhenProviderMetadataIsUnavailable() {
        when(marketDataProvider.lookupInstruments(any())).thenThrow(permissionDenied());
        when(securityInstrumentMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(securityInstrumentMapper.insert(any(SecurityInstrument.class))).thenAnswer(invocation -> {
            SecurityInstrument instrument = invocation.getArgument(0);
            instrument.setId(9L);
            return 1;
        });
        when(dailyPriceMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        List<InstrumentImportResult> results = marketDataService.importInstruments(
                new ImportInstrumentsRequest(List.of("600519"), false, null));

        assertEquals(1, results.size());
        InstrumentImportResult result = results.getFirst();
        assertEquals("600519.SH", result.canonicalCode());
        assertEquals(9L, result.instrumentId());
        assertEquals("600519.SH", result.instrumentName());
        assertEquals("CREATED", result.action());
        assertEquals("FALLBACK", result.metadataStatus());

        ArgumentCaptor<SecurityInstrument> captor = ArgumentCaptor.forClass(SecurityInstrument.class);
        verify(securityInstrumentMapper).insert(captor.capture());
        SecurityInstrument instrument = captor.getValue();
        assertEquals("600519", instrument.getSymbol());
        assertEquals(Exchange.SSE, instrument.getExchange());
        assertEquals("600519.SH", instrument.getInstrumentName());
        assertEquals(InstrumentStatus.ACTIVE, instrument.getStatus());
    }

    @Test
    void clearDailyPricesSucceedsAndDoesNotDeleteInstrument() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(syncLogMapper.exists(any())).thenReturn(false);

        marketDataService.clearDailyPrices(1L);

        verify(dailyPriceMapper).delete(any());
        verify(syncLogMapper).delete(any());
        verify(securityInstrumentMapper, never()).deleteById(1L);
    }

    @Test
    void syncFailsWhenProviderReturnsNoDailyBars() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(dailyPriceMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(marketDataProvider.providerName()).thenReturn("TUSHARE");
        when(marketDataProvider.fetchDailyBars(any(), any(), any())).thenReturn(List.of());

        DailyPriceSyncResult result = marketDataService.syncInstrumentDailyPrices(
                1L, new DailyPriceSyncRequest(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), false), "MANUAL");

        assertEquals("FAILED", result.status());
        assertEquals(0, result.recordsReceived());
        assertTrue(result.message().contains("No daily price rows returned"));
        verify(dailyPriceMapper, never()).insert(any(InstrumentDailyPrice.class));
    }

    @Test
    void clearDailyPricesCanRunRepeatedlyWithoutError() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(syncLogMapper.exists(any())).thenReturn(false);
        when(dailyPriceMapper.delete(any())).thenReturn(0);
        when(syncLogMapper.delete(any())).thenReturn(0);

        marketDataService.clearDailyPrices(1L);

        verify(dailyPriceMapper).delete(any());
        verify(syncLogMapper).delete(any());
    }

    @Test
    void clearDailyPricesRejectsRunningSync() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(syncLogMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> marketDataService.clearDailyPrices(1L));

        verify(dailyPriceMapper, never()).delete(any());
    }

    @Test
    void clearDailyPricesLeavesSummaryEmpty() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(dailyPriceMapper.selectList(any())).thenReturn(List.of());
        when(syncLogMapper.selectOne(any())).thenReturn(null);
        when(marketDataProvider.providerName()).thenReturn("Tushare");

        DailyPriceSummaryResponse summary = marketDataService.getDailyPriceSummary(1L);

        assertEquals(0, summary.recordCount());
        assertNull(summary.latestTradeDate());
        assertNull(summary.latestClose());
    }

    private SecurityInstrument activeInstrument() {
        SecurityInstrument instrument = new SecurityInstrument();
        instrument.setId(1L);
        instrument.setSymbol("600519");
        instrument.setExchange(Exchange.SSE);
        instrument.setInstrumentName("Kweichow Moutai");
        instrument.setAssetType(AssetType.STOCK);
        instrument.setCurrency("CNY");
        instrument.setStatus(InstrumentStatus.ACTIVE);
        return instrument;
    }

    private MarketDataProviderException permissionDenied() {
        return new MarketDataProviderException(MarketDataProviderErrorType.PERMISSION_DENIED,
                "No permission for stock_basic");
    }
}
