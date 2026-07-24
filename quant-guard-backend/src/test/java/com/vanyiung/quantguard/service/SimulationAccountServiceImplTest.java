package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateSimulationAccountRequest;
import com.vanyiung.quantguard.dto.SimulationAccountResponse;
import com.vanyiung.quantguard.entity.SimulationAccount;
import com.vanyiung.quantguard.enums.AccountType;
import com.vanyiung.quantguard.exception.DuplicateResourceException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.SimulationAccountMapper;
import com.vanyiung.quantguard.service.impl.SimulationAccountServiceImpl;
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
class SimulationAccountServiceImplTest {

    @Mock
    private SimulationAccountMapper simulationAccountMapper;

    private SimulationAccountServiceImpl simulationAccountService;

    @BeforeEach
    void setUp() {
        simulationAccountService = new SimulationAccountServiceImpl(simulationAccountMapper);
    }

    @Test
    void createSucceedsAndCopiesInitialCashToAvailableCash() {
        when(simulationAccountMapper.exists(any())).thenReturn(false);
        when(simulationAccountMapper.insert(any(SimulationAccount.class))).thenAnswer(invocation -> {
            SimulationAccount account = invocation.getArgument(0);
            account.setId(1L);
            return 1;
        });

        SimulationAccountResponse response = simulationAccountService.create(
                new CreateSimulationAccountRequest("  Long ETF  ", AccountType.LONG_TERM, new BigDecimal("100000.00")));

        assertEquals("Long ETF", response.accountName());
        assertEquals(new BigDecimal("100000.00"), response.initialCash());
        assertEquals(new BigDecimal("100000.00"), response.availableCash());

        ArgumentCaptor<SimulationAccount> accountCaptor = ArgumentCaptor.forClass(SimulationAccount.class);
        verify(simulationAccountMapper).insert(accountCaptor.capture());
        assertEquals("Long ETF", accountCaptor.getValue().getAccountName());
    }

    @Test
    void createRejectsDuplicateName() {
        when(simulationAccountMapper.exists(any())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> simulationAccountService.create(
                new CreateSimulationAccountRequest("Long ETF", AccountType.LONG_TERM, new BigDecimal("100000.00"))));
    }

    @Test
    void getByIdRejectsMissingAccount() {
        when(simulationAccountMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> simulationAccountService.getById(999L));
    }
}
