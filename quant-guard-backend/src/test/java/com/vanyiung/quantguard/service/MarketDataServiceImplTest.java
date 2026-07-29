package com.vanyiung.quantguard.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.vanyiung.quantguard.dto.DailyPriceSummaryResponse;
import com.vanyiung.quantguard.dto.DailyPriceSyncRequest;
import com.vanyiung.quantguard.dto.DailyPriceSyncResult;
import com.vanyiung.quantguard.dto.ImportInstrumentsRequest;
import com.vanyiung.quantguard.dto.InstrumentCatalogItem;
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
import com.vanyiung.quantguard.marketdata.provider.ProviderInstrumentMetadata;
import com.vanyiung.quantguard.marketdata.provider.ProviderInstrumentType;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;
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
    void searchCatalogReturnsDisambiguatedIndexAndStockResults() {
        ProviderInstrumentMetadata index = new ProviderInstrumentMetadata(
                new MarketSymbol("000688", Exchange.SSE),
                "科创50",
                Exchange.SSE,
                "CNY",
                "L",
                null,
                ProviderInstrumentType.INDEX);
        ProviderInstrumentMetadata stock = new ProviderInstrumentMetadata(
                new MarketSymbol("000688", Exchange.SZSE),
                "国城矿业",
                Exchange.SZSE,
                "CNY",
                "L",
                LocalDate.of(1997, 1, 20),
                ProviderInstrumentType.STOCK);
        when(marketDataProvider.searchInstruments("000688", 20)).thenReturn(List.of(index, stock));
        when(securityInstrumentMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        List<InstrumentCatalogItem> results = marketDataService.searchInstrumentCatalog("000688", 20);

        assertEquals(List.of("000688.SH", "000688.SZ"),
                results.stream().map(InstrumentCatalogItem::canonicalCode).toList());
        assertEquals(List.of(AssetType.INDEX, AssetType.STOCK),
                results.stream().map(InstrumentCatalogItem::assetType).toList());
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
    void importCreatesEtfWhenProviderMetadataIdentifiesEtf() {
        ProviderInstrumentMetadata metadata = new ProviderInstrumentMetadata(
                new MarketSymbol("510300", Exchange.SSE),
                "沪深300ETF",
                Exchange.SSE,
                "CNY",
                "L",
                LocalDate.of(2012, 5, 28),
                ProviderInstrumentType.ETF);
        when(marketDataProvider.lookupInstruments(any())).thenReturn(List.of(metadata));
        when(securityInstrumentMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(securityInstrumentMapper.insert(any(SecurityInstrument.class))).thenAnswer(invocation -> {
            SecurityInstrument instrument = invocation.getArgument(0);
            instrument.setId(10L);
            return 1;
        });
        when(dailyPriceMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        List<InstrumentImportResult> results = marketDataService.importInstruments(
                new ImportInstrumentsRequest(List.of("510300.SH"), false, null));

        assertEquals("CREATED", results.getFirst().action());
        ArgumentCaptor<SecurityInstrument> captor = ArgumentCaptor.forClass(SecurityInstrument.class);
        verify(securityInstrumentMapper).insert(captor.capture());
        assertEquals(AssetType.ETF, captor.getValue().getAssetType());
        assertEquals("沪深300ETF", captor.getValue().getInstrumentName());
    }

    @Test
    void importCreatesReadOnlyIndexWithUnitLotSize() {
        ProviderInstrumentMetadata metadata = new ProviderInstrumentMetadata(
                new MarketSymbol("000688", Exchange.SSE),
                "科创50",
                Exchange.SSE,
                "CNY",
                "L",
                null,
                ProviderInstrumentType.INDEX);
        when(marketDataProvider.lookupInstruments(any())).thenReturn(List.of(metadata));
        when(securityInstrumentMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(securityInstrumentMapper.insert(any(SecurityInstrument.class))).thenAnswer(invocation -> {
            SecurityInstrument instrument = invocation.getArgument(0);
            instrument.setId(11L);
            return 1;
        });
        when(dailyPriceMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        marketDataService.importInstruments(
                new ImportInstrumentsRequest(List.of("000688.SH"), false, null));

        ArgumentCaptor<SecurityInstrument> captor = ArgumentCaptor.forClass(SecurityInstrument.class);
        verify(securityInstrumentMapper).insert(captor.capture());
        assertEquals(AssetType.INDEX, captor.getValue().getAssetType());
        assertEquals(0, captor.getValue().getLotSize().compareTo(new java.math.BigDecimal("1")));
    }

    @Test
    void previewRejectsUnknownProviderInstrumentType() {
        ProviderInstrumentMetadata metadata = new ProviderInstrumentMetadata(
                new MarketSymbol("000001", Exchange.SZSE),
                "Unknown instrument",
                Exchange.SZSE,
                "CNY",
                "L",
                null,
                ProviderInstrumentType.UNKNOWN);
        when(marketDataProvider.lookupInstruments(any())).thenReturn(List.of(metadata));
        when(securityInstrumentMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        InstrumentImportPreviewItem item = marketDataService.previewImport(
                new InstrumentImportPreviewRequest(List.of("000001.SZ"))).getFirst();

        assertEquals(false, item.valid());
        assertNull(item.assetType());
        assertTrue(item.message().contains("UNKNOWN"));
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
