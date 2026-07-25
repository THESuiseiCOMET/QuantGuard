package com.vanyiung.quantguard.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.vanyiung.quantguard.dto.ImportInstrumentsRequest;
import com.vanyiung.quantguard.dto.InstrumentImportPreviewItem;
import com.vanyiung.quantguard.dto.InstrumentImportPreviewRequest;
import com.vanyiung.quantguard.dto.InstrumentImportResult;
import com.vanyiung.quantguard.entity.SecurityInstrument;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.enums.InstrumentStatus;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    private MarketDataProviderException permissionDenied() {
        return new MarketDataProviderException(MarketDataProviderErrorType.PERMISSION_DENIED,
                "No permission for stock_basic");
    }
}
