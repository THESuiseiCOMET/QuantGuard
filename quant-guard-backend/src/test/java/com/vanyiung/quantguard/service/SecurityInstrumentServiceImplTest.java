package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateSecurityInstrumentRequest;
import com.vanyiung.quantguard.dto.SecurityInstrumentResponse;
import com.vanyiung.quantguard.entity.SecurityInstrument;
import com.vanyiung.quantguard.enums.AssetType;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.enums.InstrumentStatus;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.DuplicateResourceException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.CashLedgerMapper;
import com.vanyiung.quantguard.mapper.InstrumentDailyPriceMapper;
import com.vanyiung.quantguard.mapper.MarketDataSyncLogMapper;
import com.vanyiung.quantguard.mapper.PortfolioPositionMapper;
import com.vanyiung.quantguard.mapper.SecurityInstrumentMapper;
import com.vanyiung.quantguard.mapper.SimulatedOrderMapper;
import com.vanyiung.quantguard.mapper.SimulatedTradeMapper;
import com.vanyiung.quantguard.service.impl.SecurityInstrumentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityInstrumentServiceImplTest {

    @Mock
    private SecurityInstrumentMapper securityInstrumentMapper;

    @Mock
    private PortfolioPositionMapper portfolioPositionMapper;

    @Mock
    private SimulatedOrderMapper simulatedOrderMapper;

    @Mock
    private SimulatedTradeMapper simulatedTradeMapper;

    @Mock
    private CashLedgerMapper cashLedgerMapper;

    @Mock
    private InstrumentDailyPriceMapper dailyPriceMapper;

    @Mock
    private MarketDataSyncLogMapper syncLogMapper;

    private SecurityInstrumentServiceImpl securityInstrumentService;

    @BeforeEach
    void setUp() {
        securityInstrumentService = new SecurityInstrumentServiceImpl(securityInstrumentMapper,
                portfolioPositionMapper, simulatedOrderMapper, simulatedTradeMapper, cashLedgerMapper,
                dailyPriceMapper, syncLogMapper);
    }

    @Test
    void createSucceedsAndNormalizesSymbolCurrencyAndName() {
        when(securityInstrumentMapper.exists(any())).thenReturn(false);
        when(securityInstrumentMapper.insert(any(SecurityInstrument.class))).thenAnswer(invocation -> {
            SecurityInstrument instrument = invocation.getArgument(0);
            instrument.setId(1L);
            return 1;
        });

        SecurityInstrumentResponse response = securityInstrumentService.create(
                new CreateSecurityInstrumentRequest("  aapl  ", Exchange.NASDAQ, "  Apple Inc  ",
                        AssetType.STOCK, " usd ", new BigDecimal("1.000000")));

        assertEquals("AAPL", response.symbol());
        assertEquals("USD", response.currency());
        assertEquals("Apple Inc", response.instrumentName());

        ArgumentCaptor<SecurityInstrument> captor = ArgumentCaptor.forClass(SecurityInstrument.class);
        verify(securityInstrumentMapper).insert(captor.capture());
        assertEquals("AAPL", captor.getValue().getSymbol());
        assertEquals("USD", captor.getValue().getCurrency());
    }

    @Test
    void createRejectsDuplicateExchangeAndSymbol() {
        when(securityInstrumentMapper.exists(any())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> securityInstrumentService.create(
                new CreateSecurityInstrumentRequest("AAPL", Exchange.NASDAQ, "Apple Inc",
                        AssetType.STOCK, "USD", new BigDecimal("1.000000"))));
    }

    @Test
    void getByIdRejectsMissingInstrument() {
        when(securityInstrumentMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> securityInstrumentService.getById(999L));
    }

    @Test
    void deleteEmptyInstrumentSucceedsAndClearsMarketData() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(syncLogMapper.exists(any())).thenReturn(false);
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(false);
        when(cashLedgerMapper.exists(any())).thenReturn(false);
        when(securityInstrumentMapper.deleteById(1L)).thenReturn(1);

        securityInstrumentService.delete(1L);

        verify(dailyPriceMapper).delete(any());
        verify(syncLogMapper).delete(any());
        verify(securityInstrumentMapper).deleteById(1L);
    }

    @Test
    void deleteInstrumentWithPositionFails() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(syncLogMapper.exists(any())).thenReturn(false);
        when(portfolioPositionMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> securityInstrumentService.delete(1L));

        verify(securityInstrumentMapper, never()).deleteById(1L);
    }

    @Test
    void deleteInstrumentWithOpenOrderFails() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(syncLogMapper.exists(any())).thenReturn(false);
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(simulatedOrderMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> securityInstrumentService.delete(1L));

        verify(securityInstrumentMapper, never()).deleteById(1L);
    }

    @Test
    void deleteInstrumentWithTradeFails() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(syncLogMapper.exists(any())).thenReturn(false);
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> securityInstrumentService.delete(1L));

        verify(securityInstrumentMapper, never()).deleteById(1L);
    }

    @Test
    void deleteInstrumentWithCashLedgerFails() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(syncLogMapper.exists(any())).thenReturn(false);
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(false);
        when(cashLedgerMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> securityInstrumentService.delete(1L));

        verify(securityInstrumentMapper, never()).deleteById(1L);
    }

    @Test
    void deleteInstrumentWithRunningSyncFails() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(syncLogMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> securityInstrumentService.delete(1L));

        verify(securityInstrumentMapper, never()).deleteById(1L);
    }

    @Test
    void disableUnusedInstrumentSucceeds() {
        SecurityInstrument instrument = activeInstrument();
        when(securityInstrumentMapper.selectById(1L)).thenReturn(instrument);
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(securityInstrumentMapper.updateById(any(SecurityInstrument.class))).thenReturn(1);

        SecurityInstrumentResponse response = securityInstrumentService.updateStatus(1L, InstrumentStatus.DISABLED);

        assertEquals(InstrumentStatus.DISABLED, response.status());
    }

    @Test
    void disableInstrumentWithOpenOrderFails() {
        when(securityInstrumentMapper.selectById(1L)).thenReturn(activeInstrument());
        when(simulatedOrderMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class,
                () -> securityInstrumentService.updateStatus(1L, InstrumentStatus.DISABLED));
    }

    @Test
    void restoreInstrumentSucceeds() {
        SecurityInstrument instrument = activeInstrument();
        instrument.setStatus(InstrumentStatus.DISABLED);
        when(securityInstrumentMapper.selectById(1L)).thenReturn(instrument);
        when(securityInstrumentMapper.updateById(any(SecurityInstrument.class))).thenReturn(1);

        SecurityInstrumentResponse response = securityInstrumentService.updateStatus(1L, InstrumentStatus.ACTIVE);

        assertEquals(InstrumentStatus.ACTIVE, response.status());
    }

    private SecurityInstrument activeInstrument() {
        SecurityInstrument instrument = new SecurityInstrument();
        instrument.setId(1L);
        instrument.setSymbol("600519");
        instrument.setExchange(Exchange.SSE);
        instrument.setInstrumentName("Kweichow Moutai");
        instrument.setAssetType(AssetType.STOCK);
        instrument.setCurrency("CNY");
        instrument.setLotSize(new BigDecimal("100.000000"));
        instrument.setStatus(InstrumentStatus.ACTIVE);
        return instrument;
    }
}
