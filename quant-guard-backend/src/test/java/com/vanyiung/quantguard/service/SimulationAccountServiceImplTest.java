package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateSimulationAccountRequest;
import com.vanyiung.quantguard.dto.SimulationAccountResponse;
import com.vanyiung.quantguard.entity.CashLedger;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
import com.vanyiung.quantguard.entity.SimulatedOrder;
import com.vanyiung.quantguard.entity.SimulatedTrade;
import com.vanyiung.quantguard.entity.SimulationAccount;
import com.vanyiung.quantguard.enums.AccountStatus;
import com.vanyiung.quantguard.enums.AccountType;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.DuplicateResourceException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.CashLedgerMapper;
import com.vanyiung.quantguard.mapper.InvestmentPortfolioMapper;
import com.vanyiung.quantguard.mapper.PortfolioPositionMapper;
import com.vanyiung.quantguard.mapper.SimulatedOrderMapper;
import com.vanyiung.quantguard.mapper.SimulatedTradeMapper;
import com.vanyiung.quantguard.mapper.SimulationAccountMapper;
import com.vanyiung.quantguard.service.impl.SimulationAccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationAccountServiceImplTest {

    @Mock
    private SimulationAccountMapper simulationAccountMapper;

    @Mock
    private InvestmentPortfolioMapper investmentPortfolioMapper;

    @Mock
    private PortfolioPositionMapper portfolioPositionMapper;

    @Mock
    private SimulatedOrderMapper simulatedOrderMapper;

    @Mock
    private SimulatedTradeMapper simulatedTradeMapper;

    @Mock
    private CashLedgerMapper cashLedgerMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SimulationAccountServiceImpl simulationAccountService;

    @BeforeEach
    void setUp() {
        simulationAccountService = new SimulationAccountServiceImpl(simulationAccountMapper,
                investmentPortfolioMapper, portfolioPositionMapper, simulatedOrderMapper, simulatedTradeMapper,
                cashLedgerMapper, jdbcTemplate);
    }

    @Test
    void createSucceedsAndCopiesInitialCashToAvailableCash() {
        when(simulationAccountMapper.exists(any())).thenReturn(false);
        when(simulationAccountMapper.insert(any(SimulationAccount.class))).thenAnswer(invocation -> {
            SimulationAccount account = invocation.getArgument(0);
            account.setId(1L);
            return 1;
        });
        when(investmentPortfolioMapper.insert(any(InvestmentPortfolio.class))).thenReturn(1);

        SimulationAccountResponse response = simulationAccountService.create(
                new CreateSimulationAccountRequest("  Long ETF  ", AccountType.LONG_TERM, new BigDecimal("100000.00")));

        assertEquals("Long ETF", response.accountName());
        assertEquals(new BigDecimal("100000.00"), response.initialCash());
        assertEquals(new BigDecimal("100000.00"), response.availableCash());

        ArgumentCaptor<SimulationAccount> accountCaptor = ArgumentCaptor.forClass(SimulationAccount.class);
        verify(simulationAccountMapper).insert(accountCaptor.capture());
        assertEquals("Long ETF", accountCaptor.getValue().getAccountName());

        ArgumentCaptor<InvestmentPortfolio> portfolioCaptor =
                ArgumentCaptor.forClass(InvestmentPortfolio.class);
        verify(investmentPortfolioMapper).insert(portfolioCaptor.capture());
        assertEquals(1L, portfolioCaptor.getValue().getAccountId());
        assertEquals("默认组合", portfolioCaptor.getValue().getPortfolioName());
        assertEquals(AccountType.LONG_TERM, portfolioCaptor.getValue().getPortfolioType());
        assertEquals(AccountStatus.ACTIVE, portfolioCaptor.getValue().getStatus());
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

    @Test
    void emptyAccountCanBeDeleted() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account());
        when(investmentPortfolioMapper.selectList(any())).thenReturn(List.of());
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(false);
        when(cashLedgerMapper.exists(any())).thenReturn(false);
        when(simulationAccountMapper.deleteById(1L)).thenReturn(1);

        simulationAccountService.delete(1L);

        verify(simulationAccountMapper).deleteById(1L);
    }

    @Test
    void accountWithOnlyEmptyPortfoliosCanBeDeleted() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account());
        when(investmentPortfolioMapper.selectList(any())).thenReturn(List.of(portfolio(10L)));
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(false);
        when(cashLedgerMapper.exists(any())).thenReturn(false);
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(simulationAccountMapper.deleteById(1L)).thenReturn(1);

        simulationAccountService.delete(1L);

        verify(jdbcTemplate).update("DELETE FROM portfolio_risk_config WHERE portfolio_id = ?", 10L);
        verify(investmentPortfolioMapper).delete(any());
        verify(simulationAccountMapper).deleteById(1L);
    }

    @Test
    void accountWithPortfolioPositionCannotBeDeleted() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account());
        when(investmentPortfolioMapper.selectList(any())).thenReturn(List.of(portfolio(10L)));
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(false);
        when(cashLedgerMapper.exists(any())).thenReturn(false);
        when(portfolioPositionMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> simulationAccountService.delete(1L));

        verify(simulationAccountMapper, never()).deleteById(1L);
    }

    @Test
    void accountWithOrderCannotBeDeleted() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account());
        when(investmentPortfolioMapper.selectList(any())).thenReturn(List.of());
        when(simulatedOrderMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> simulationAccountService.delete(1L));

        verify(simulationAccountMapper, never()).deleteById(1L);
    }

    @Test
    void accountWithTradeCannotBeDeleted() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account());
        when(investmentPortfolioMapper.selectList(any())).thenReturn(List.of());
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> simulationAccountService.delete(1L));

        verify(simulationAccountMapper, never()).deleteById(1L);
    }

    @Test
    void accountWithCashLedgerCannotBeDeleted() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account());
        when(investmentPortfolioMapper.selectList(any())).thenReturn(List.of());
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(false);
        when(cashLedgerMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> simulationAccountService.delete(1L));

        verify(simulationAccountMapper, never()).deleteById(1L);
    }

    @Test
    void forceDeleteRemovesAccountSimulationData() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account());
        when(investmentPortfolioMapper.selectList(any())).thenReturn(List.of(portfolio(10L), portfolio(11L)));
        when(simulationAccountMapper.deleteById(1L)).thenReturn(1);

        simulationAccountService.forceDelete(1L);

        verify(cashLedgerMapper).delete(any());
        verify(simulatedTradeMapper).delete(any());
        verify(simulatedOrderMapper).delete(any());
        verify(portfolioPositionMapper).delete(any());
        verify(jdbcTemplate).update("DELETE FROM portfolio_risk_config WHERE portfolio_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM portfolio_risk_config WHERE portfolio_id = ?", 11L);
        verify(investmentPortfolioMapper).delete(any());
        verify(simulationAccountMapper).deleteById(1L);
    }

    @Test
    void forceDeleteRejectsMissingAccount() {
        when(simulationAccountMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> simulationAccountService.forceDelete(999L));

        verify(simulationAccountMapper, never()).deleteById(999L);
    }

    @Test
    void frozenCashAccountCannotBeDisabled() {
        SimulationAccount account = account();
        account.setFrozenCash(new BigDecimal("1.0000"));
        when(simulationAccountMapper.selectById(1L)).thenReturn(account);

        assertThrows(BusinessRuleException.class,
                () -> simulationAccountService.updateStatus(1L, AccountStatus.DISABLED));

        verify(simulationAccountMapper, never()).updateById(any(SimulationAccount.class));
    }

    @Test
    void accountWithOpenOrderCannotBeDisabled() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account());
        when(simulatedOrderMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class,
                () -> simulationAccountService.updateStatus(1L, AccountStatus.DISABLED));

        verify(simulationAccountMapper, never()).updateById(any(SimulationAccount.class));
    }

    @Test
    void accountCanBeDisabled() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account());
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);

        SimulationAccountResponse response = simulationAccountService.updateStatus(1L, AccountStatus.DISABLED);

        assertEquals(AccountStatus.DISABLED, response.status());
    }

    @Test
    void disabledAccountCanBeRestored() {
        SimulationAccount account = account();
        account.setStatus(AccountStatus.DISABLED);
        when(simulationAccountMapper.selectById(1L)).thenReturn(account);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);

        SimulationAccountResponse response = simulationAccountService.updateStatus(1L, AccountStatus.ACTIVE);

        assertEquals(AccountStatus.ACTIVE, response.status());
    }

    private SimulationAccount account() {
        SimulationAccount account = new SimulationAccount();
        account.setId(1L);
        account.setAccountName("Long ETF");
        account.setAccountType(AccountType.LONG_TERM);
        account.setInitialCash(new BigDecimal("100000.0000"));
        account.setAvailableCash(new BigDecimal("100000.0000"));
        account.setFrozenCash(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(0);
        return account;
    }

    private InvestmentPortfolio portfolio(Long id) {
        InvestmentPortfolio portfolio = new InvestmentPortfolio();
        portfolio.setId(id);
        portfolio.setAccountId(1L);
        portfolio.setPortfolioName("Core ETF");
        portfolio.setPortfolioType(AccountType.LONG_TERM);
        portfolio.setStatus(AccountStatus.ACTIVE);
        return portfolio;
    }
}
