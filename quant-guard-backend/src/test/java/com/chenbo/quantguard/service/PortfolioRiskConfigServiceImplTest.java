package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.CreatePortfolioRiskConfigRequest;
import com.chenbo.quantguard.dto.PortfolioRiskConfigResponse;
import com.chenbo.quantguard.entity.InvestmentPortfolio;
import com.chenbo.quantguard.entity.PortfolioRiskConfig;
import com.chenbo.quantguard.enums.AccountStatus;
import com.chenbo.quantguard.exception.DuplicateResourceException;
import com.chenbo.quantguard.exception.ResourceNotFoundException;
import com.chenbo.quantguard.mapper.InvestmentPortfolioMapper;
import com.chenbo.quantguard.mapper.PortfolioRiskConfigMapper;
import com.chenbo.quantguard.service.impl.PortfolioRiskConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioRiskConfigServiceImplTest {

    @Mock
    private PortfolioRiskConfigMapper portfolioRiskConfigMapper;

    @Mock
    private InvestmentPortfolioMapper investmentPortfolioMapper;

    private PortfolioRiskConfigServiceImpl portfolioRiskConfigService;

    @BeforeEach
    void setUp() {
        portfolioRiskConfigService = new PortfolioRiskConfigServiceImpl(portfolioRiskConfigMapper, investmentPortfolioMapper);
    }

    @Test
    void createSucceeds() {
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(activePortfolio());
        when(portfolioRiskConfigMapper.exists(any())).thenReturn(false);
        when(portfolioRiskConfigMapper.insert(any(PortfolioRiskConfig.class))).thenAnswer(invocation -> {
            PortfolioRiskConfig config = invocation.getArgument(0);
            config.setId(10L);
            return 1;
        });

        PortfolioRiskConfigResponse response = portfolioRiskConfigService.create(new CreatePortfolioRiskConfigRequest(
                1L, new BigDecimal("50000.0000"), new BigDecimal("0.500000"), true, true));

        assertEquals(1L, response.portfolioId());
        assertEquals(new BigDecimal("50000.0000"), response.maxSingleOrderAmount());
    }

    @Test
    void createRejectsMissingPortfolio() {
        when(investmentPortfolioMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> portfolioRiskConfigService.create(new CreatePortfolioRiskConfigRequest(
                999L, new BigDecimal("50000.0000"), new BigDecimal("0.500000"), true, true)));
    }

    @Test
    void createRejectsDuplicateConfig() {
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(activePortfolio());
        when(portfolioRiskConfigMapper.exists(any())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> portfolioRiskConfigService.create(new CreatePortfolioRiskConfigRequest(
                1L, new BigDecimal("50000.0000"), new BigDecimal("0.500000"), true, true)));
    }

    @Test
    void getByIdRejectsMissingConfig() {
        when(portfolioRiskConfigMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> portfolioRiskConfigService.getById(999L));
    }

    @Test
    void getByPortfolioIdRejectsMissingConfig() {
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(activePortfolio());
        when(portfolioRiskConfigMapper.selectOne(any())).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> portfolioRiskConfigService.getByPortfolioId(1L));
    }

    private InvestmentPortfolio activePortfolio() {
        InvestmentPortfolio portfolio = new InvestmentPortfolio();
        portfolio.setId(1L);
        portfolio.setStatus(AccountStatus.ACTIVE);
        return portfolio;
    }
}
