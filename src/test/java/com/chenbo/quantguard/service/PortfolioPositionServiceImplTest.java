package com.chenbo.quantguard.service;

import com.chenbo.quantguard.entity.InvestmentPortfolio;
import com.chenbo.quantguard.exception.ResourceNotFoundException;
import com.chenbo.quantguard.mapper.InvestmentPortfolioMapper;
import com.chenbo.quantguard.mapper.PortfolioPositionMapper;
import com.chenbo.quantguard.service.impl.PortfolioPositionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioPositionServiceImplTest {

    @Mock
    private PortfolioPositionMapper portfolioPositionMapper;

    @Mock
    private InvestmentPortfolioMapper investmentPortfolioMapper;

    private PortfolioPositionServiceImpl portfolioPositionService;

    @BeforeEach
    void setUp() {
        portfolioPositionService = new PortfolioPositionServiceImpl(portfolioPositionMapper, investmentPortfolioMapper);
    }

    @Test
    void validPortfolioWithNoPositionsReturnsEmptyList() {
        InvestmentPortfolio portfolio = new InvestmentPortfolio();
        portfolio.setId(1L);
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio);
        when(portfolioPositionMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(), portfolioPositionService.listByPortfolioId(1L));
    }

    @Test
    void listByPortfolioIdRejectsMissingPortfolio() {
        when(investmentPortfolioMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> portfolioPositionService.listByPortfolioId(999L));
    }

    @Test
    void getByIdRejectsMissingPosition() {
        when(portfolioPositionMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> portfolioPositionService.getById(999L));
    }
}
