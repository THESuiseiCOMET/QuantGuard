package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateInvestmentPortfolioRequest;
import com.vanyiung.quantguard.dto.InvestmentPortfolioResponse;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
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
import com.vanyiung.quantguard.service.impl.InvestmentPortfolioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentPortfolioServiceImplTest {

    @Mock
    private InvestmentPortfolioMapper investmentPortfolioMapper;

    @Mock
    private SimulationAccountMapper simulationAccountMapper;

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

    private InvestmentPortfolioServiceImpl investmentPortfolioService;

    @BeforeEach
    void setUp() {
        investmentPortfolioService = new InvestmentPortfolioServiceImpl(investmentPortfolioMapper, simulationAccountMapper,
                portfolioPositionMapper, simulatedOrderMapper, simulatedTradeMapper, cashLedgerMapper, jdbcTemplate);
    }

    @Test
    void createSucceeds() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(1L, AccountType.LONG_TERM));
        when(investmentPortfolioMapper.exists(any())).thenReturn(false);
        when(investmentPortfolioMapper.insert(any(InvestmentPortfolio.class))).thenAnswer(invocation -> {
            InvestmentPortfolio portfolio = invocation.getArgument(0);
            portfolio.setId(10L);
            return 1;
        });

        InvestmentPortfolioResponse response = investmentPortfolioService.create(
                new CreateInvestmentPortfolioRequest(1L, "  Core ETF  ", AccountType.LONG_TERM, "  long plan  "));

        assertEquals("Core ETF", response.portfolioName());
        assertEquals("long plan", response.description());

        ArgumentCaptor<InvestmentPortfolio> portfolioCaptor = ArgumentCaptor.forClass(InvestmentPortfolio.class);
        verify(investmentPortfolioMapper).insert(portfolioCaptor.capture());
        assertEquals("Core ETF", portfolioCaptor.getValue().getPortfolioName());
        assertEquals("long plan", portfolioCaptor.getValue().getDescription());
    }

    @Test
    void createRejectsMissingAccount() {
        when(simulationAccountMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> investmentPortfolioService.create(
                new CreateInvestmentPortfolioRequest(999L, "Core ETF", AccountType.LONG_TERM, null)));
    }

    @Test
    void createRejectsTypeMismatch() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(1L, AccountType.LONG_TERM));

        assertThrows(BusinessRuleException.class, () -> investmentPortfolioService.create(
                new CreateInvestmentPortfolioRequest(1L, "Short Trade", AccountType.SHORT_TERM, null)));
    }

    @Test
    void createRejectsDisabledAccount() {
        SimulationAccount account = account(1L, AccountType.LONG_TERM);
        account.setStatus(AccountStatus.DISABLED);
        when(simulationAccountMapper.selectById(1L)).thenReturn(account);

        assertThrows(BusinessRuleException.class, () -> investmentPortfolioService.create(
                new CreateInvestmentPortfolioRequest(1L, "Core ETF", AccountType.LONG_TERM, null)));
    }

    @Test
    void createRejectsDuplicateNameInSameAccount() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(1L, AccountType.LONG_TERM));
        when(investmentPortfolioMapper.exists(any())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> investmentPortfolioService.create(
                new CreateInvestmentPortfolioRequest(1L, "Core ETF", AccountType.LONG_TERM, null)));
    }

    @Test
    void sameNameCanBeCreatedInDifferentAccountsWhenMapperReportsNoConflict() {
        when(simulationAccountMapper.selectById(2L)).thenReturn(account(2L, AccountType.LONG_TERM));
        when(investmentPortfolioMapper.exists(any())).thenReturn(false);
        when(investmentPortfolioMapper.insert(any(InvestmentPortfolio.class))).thenAnswer(invocation -> {
            InvestmentPortfolio portfolio = invocation.getArgument(0);
            portfolio.setId(20L);
            return 1;
        });

        InvestmentPortfolioResponse response = investmentPortfolioService.create(
                new CreateInvestmentPortfolioRequest(2L, "Core ETF", AccountType.LONG_TERM, null));

        assertEquals(2L, response.accountId());
        assertEquals("Core ETF", response.portfolioName());
    }

    @Test
    void getByIdRejectsMissingPortfolio() {
        when(investmentPortfolioMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> investmentPortfolioService.getById(999L));
    }

    @Test
    void emptyPortfolioCanBeDeleted() {
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio());
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(false);
        when(cashLedgerMapper.exists(any())).thenReturn(false);
        when(investmentPortfolioMapper.deleteById(10L)).thenReturn(1);

        investmentPortfolioService.delete(10L);

        verify(investmentPortfolioMapper).deleteById(10L);
    }

    @Test
    void portfolioWithOnlyLegacyRiskConfigCanBeDeleted() {
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio());
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(false);
        when(cashLedgerMapper.exists(any())).thenReturn(false);
        when(investmentPortfolioMapper.deleteById(10L)).thenReturn(1);

        investmentPortfolioService.delete(10L);

        verify(jdbcTemplate).update("DELETE FROM portfolio_risk_config WHERE portfolio_id = ?", 10L);
        verify(investmentPortfolioMapper).deleteById(10L);
    }

    @Test
    void portfolioWithPositionCannotBeDeleted() {
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio());
        when(portfolioPositionMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> investmentPortfolioService.delete(10L));

        verify(investmentPortfolioMapper, never()).deleteById(10L);
    }

    @Test
    void portfolioWithOrderCannotBeDeleted() {
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio());
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(simulatedOrderMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> investmentPortfolioService.delete(10L));

        verify(investmentPortfolioMapper, never()).deleteById(10L);
    }

    @Test
    void portfolioWithTradeCannotBeDeleted() {
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio());
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> investmentPortfolioService.delete(10L));

        verify(investmentPortfolioMapper, never()).deleteById(10L);
    }

    @Test
    void portfolioWithCashLedgerCannotBeDeleted() {
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio());
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(simulatedTradeMapper.exists(any())).thenReturn(false);
        when(cashLedgerMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> investmentPortfolioService.delete(10L));

        verify(investmentPortfolioMapper, never()).deleteById(10L);
    }

    @Test
    void forceDeleteRemovesPortfolioSimulationData() {
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio());
        when(investmentPortfolioMapper.deleteById(10L)).thenReturn(1);

        investmentPortfolioService.forceDelete(10L);

        verify(cashLedgerMapper).delete(any());
        verify(simulatedTradeMapper).delete(any());
        verify(simulatedOrderMapper).delete(any());
        verify(portfolioPositionMapper).delete(any());
        verify(jdbcTemplate).update("DELETE FROM portfolio_risk_config WHERE portfolio_id = ?", 10L);
        verify(investmentPortfolioMapper).deleteById(10L);
    }

    @Test
    void forceDeleteRejectsMissingPortfolio() {
        when(investmentPortfolioMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> investmentPortfolioService.forceDelete(999L));

        verify(investmentPortfolioMapper, never()).deleteById(999L);
    }

    @Test
    void portfolioWithOpenOrderCannotBeDisabled() {
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio());
        when(simulatedOrderMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class,
                () -> investmentPortfolioService.updateStatus(10L, AccountStatus.DISABLED));

        verify(investmentPortfolioMapper, never()).updateById(any(InvestmentPortfolio.class));
    }

    @Test
    void portfolioWithFrozenPositionCannotBeDisabled() {
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio());
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(portfolioPositionMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class,
                () -> investmentPortfolioService.updateStatus(10L, AccountStatus.DISABLED));

        verify(investmentPortfolioMapper, never()).updateById(any(InvestmentPortfolio.class));
    }

    @Test
    void portfolioCanBeDisabled() {
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio());
        when(simulatedOrderMapper.exists(any())).thenReturn(false);
        when(portfolioPositionMapper.exists(any())).thenReturn(false);
        when(investmentPortfolioMapper.updateById(any(InvestmentPortfolio.class))).thenReturn(1);

        InvestmentPortfolioResponse response = investmentPortfolioService.updateStatus(10L, AccountStatus.DISABLED);

        assertEquals(AccountStatus.DISABLED, response.status());
    }

    @Test
    void disabledPortfolioCanBeRestored() {
        InvestmentPortfolio portfolio = portfolio();
        portfolio.setStatus(AccountStatus.DISABLED);
        when(investmentPortfolioMapper.selectById(10L)).thenReturn(portfolio);
        when(investmentPortfolioMapper.updateById(any(InvestmentPortfolio.class))).thenReturn(1);

        InvestmentPortfolioResponse response = investmentPortfolioService.updateStatus(10L, AccountStatus.ACTIVE);

        assertEquals(AccountStatus.ACTIVE, response.status());
    }

    private SimulationAccount account(Long id, AccountType accountType) {
        SimulationAccount account = new SimulationAccount();
        account.setId(id);
        account.setAccountType(accountType);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }

    private InvestmentPortfolio portfolio() {
        InvestmentPortfolio portfolio = new InvestmentPortfolio();
        portfolio.setId(10L);
        portfolio.setAccountId(1L);
        portfolio.setPortfolioName("Core ETF");
        portfolio.setPortfolioType(AccountType.LONG_TERM);
        portfolio.setStatus(AccountStatus.ACTIVE);
        return portfolio;
    }
}
