package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.CreateSecurityInstrumentRequest;
import com.chenbo.quantguard.dto.SecurityInstrumentResponse;
import com.chenbo.quantguard.entity.SecurityInstrument;
import com.chenbo.quantguard.enums.AssetType;
import com.chenbo.quantguard.enums.Exchange;
import com.chenbo.quantguard.exception.DuplicateResourceException;
import com.chenbo.quantguard.exception.ResourceNotFoundException;
import com.chenbo.quantguard.mapper.SecurityInstrumentMapper;
import com.chenbo.quantguard.service.impl.SecurityInstrumentServiceImpl;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityInstrumentServiceImplTest {

    @Mock
    private SecurityInstrumentMapper securityInstrumentMapper;

    private SecurityInstrumentServiceImpl securityInstrumentService;

    @BeforeEach
    void setUp() {
        securityInstrumentService = new SecurityInstrumentServiceImpl(securityInstrumentMapper);
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
}
