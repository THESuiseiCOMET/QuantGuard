package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateInvestmentPortfolioRequest;
import com.vanyiung.quantguard.dto.InvestmentPortfolioResponse;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
import com.vanyiung.quantguard.entity.SimulationAccount;
import com.vanyiung.quantguard.enums.AccountType;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.DuplicateResourceException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.InvestmentPortfolioMapper;
import com.vanyiung.quantguard.mapper.SimulationAccountMapper;
import com.vanyiung.quantguard.service.impl.InvestmentPortfolioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentPortfolioServiceImplTest {

    @Mock
    private InvestmentPortfolioMapper investmentPortfolioMapper;

    @Mock
    private SimulationAccountMapper simulationAccountMapper;

    private InvestmentPortfolioServiceImpl investmentPortfolioService;

    @BeforeEach
    void setUp() {
        investmentPortfolioService = new InvestmentPortfolioServiceImpl(investmentPortfolioMapper, simulationAccountMapper);
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

    private SimulationAccount account(Long id, AccountType accountType) {
        SimulationAccount account = new SimulationAccount();
        account.setId(id);
        account.setAccountType(accountType);
        return account;
    }
}
