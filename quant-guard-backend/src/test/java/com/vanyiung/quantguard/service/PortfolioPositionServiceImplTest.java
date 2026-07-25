package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.UpdatePortfolioPositionRequest;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
import com.vanyiung.quantguard.entity.PortfolioPosition;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.InvestmentPortfolioMapper;
import com.vanyiung.quantguard.mapper.PortfolioPositionMapper;
import com.vanyiung.quantguard.service.impl.PortfolioPositionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void validPortfolioWithPositionsReturnsMappedFields() {
        InvestmentPortfolio portfolio = new InvestmentPortfolio();
        portfolio.setId(1L);
        PortfolioPosition position = new PortfolioPosition();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 25, 4, 10);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 25, 4, 11);
        position.setId(10L);
        position.setPortfolioId(1L);
        position.setInstrumentId(20L);
        position.setTotalQuantity(new BigDecimal("100.000000"));
        position.setAvailableQuantity(new BigDecimal("80.000000"));
        position.setFrozenQuantity(new BigDecimal("20.000000"));
        position.setAverageCost(new BigDecimal("12.3456"));
        position.setCreatedAt(createdAt);
        position.setUpdatedAt(updatedAt);

        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio);
        when(portfolioPositionMapper.selectList(any())).thenReturn(List.of(position));

        var positions = portfolioPositionService.listByPortfolioId(1L);

        assertEquals(1, positions.size());
        assertEquals(10L, positions.getFirst().id());
        assertEquals(1L, positions.getFirst().portfolioId());
        assertEquals(20L, positions.getFirst().instrumentId());
        assertEquals(new BigDecimal("100.000000"), positions.getFirst().totalQuantity());
        assertEquals(new BigDecimal("80.000000"), positions.getFirst().availableQuantity());
        assertEquals(new BigDecimal("20.000000"), positions.getFirst().frozenQuantity());
        assertEquals(new BigDecimal("12.3456"), positions.getFirst().averageCost());
        assertEquals(createdAt, positions.getFirst().createdAt());
        assertEquals(updatedAt, positions.getFirst().updatedAt());
    }

    @Test
    void listByPortfolioIdRejectsMissingPortfolio() {
        when(investmentPortfolioMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> portfolioPositionService.listByPortfolioId(999L));
    }

    @Test
    void listByPortfolioIdPropagatesDatabaseFailures() {
        InvestmentPortfolio portfolio = new InvestmentPortfolio();
        portfolio.setId(1L);
        RuntimeException databaseFailure = new RuntimeException("portfolio_position query failed");
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio);
        when(portfolioPositionMapper.selectList(any())).thenThrow(databaseFailure);

        assertThrows(RuntimeException.class, () -> portfolioPositionService.listByPortfolioId(1L));
    }

    @Test
    void getByIdRejectsMissingPosition() {
        when(portfolioPositionMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> portfolioPositionService.getById(999L));
    }

    @Test
    void positionCanBeUpdatedManually() {
        when(portfolioPositionMapper.selectById(10L)).thenReturn(position());
        when(portfolioPositionMapper.updateById(any(PortfolioPosition.class))).thenReturn(1);

        var response = portfolioPositionService.update(10L, new UpdatePortfolioPositionRequest(
                new BigDecimal("120.0000"), new BigDecimal("115.0000"), new BigDecimal("11.2300")));

        assertEquals(new BigDecimal("120.0000"), response.totalQuantity());
        assertEquals(new BigDecimal("115.0000"), response.availableQuantity());
        assertEquals(new BigDecimal("11.2300"), response.averageCost());
    }

    @Test
    void updateRejectsAvailableQuantityAboveTotalQuantity() {
        when(portfolioPositionMapper.selectById(10L)).thenReturn(position());

        assertThrows(BusinessRuleException.class, () -> portfolioPositionService.update(10L,
                new UpdatePortfolioPositionRequest(new BigDecimal("100.0000"), new BigDecimal("101.0000"), BigDecimal.ONE)));

        verify(portfolioPositionMapper, never()).updateById(any(PortfolioPosition.class));
    }

    @Test
    void updateRejectsTotalQuantityBelowFrozenQuantity() {
        PortfolioPosition position = position();
        position.setFrozenQuantity(new BigDecimal("10.0000"));
        when(portfolioPositionMapper.selectById(10L)).thenReturn(position);

        assertThrows(BusinessRuleException.class, () -> portfolioPositionService.update(10L,
                new UpdatePortfolioPositionRequest(new BigDecimal("9.0000"), new BigDecimal("9.0000"), BigDecimal.ONE)));

        verify(portfolioPositionMapper, never()).updateById(any(PortfolioPosition.class));
    }

    @Test
    void zeroQuantityPositionCanBeDeleted() {
        PortfolioPosition position = position();
        position.setTotalQuantity(BigDecimal.ZERO);
        position.setAvailableQuantity(BigDecimal.ZERO);
        position.setFrozenQuantity(BigDecimal.ZERO);
        when(portfolioPositionMapper.selectById(10L)).thenReturn(position);
        when(portfolioPositionMapper.deleteById(10L)).thenReturn(1);

        portfolioPositionService.delete(10L);

        verify(portfolioPositionMapper).deleteById(10L);
    }

    @Test
    void positionWithQuantityCannotBeDeleted() {
        when(portfolioPositionMapper.selectById(10L)).thenReturn(position());

        assertThrows(BusinessRuleException.class, () -> portfolioPositionService.delete(10L));

        verify(portfolioPositionMapper, never()).deleteById(10L);
    }

    @Test
    void positionWithFrozenQuantityCannotBeDeleted() {
        PortfolioPosition position = position();
        position.setFrozenQuantity(new BigDecimal("1.000000"));
        when(portfolioPositionMapper.selectById(10L)).thenReturn(position);

        assertThrows(BusinessRuleException.class, () -> portfolioPositionService.delete(10L));

        verify(portfolioPositionMapper, never()).deleteById(10L);
    }

    @Test
    void deleteRejectsMissingPosition() {
        when(portfolioPositionMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> portfolioPositionService.delete(999L));
    }

    private PortfolioPosition position() {
        PortfolioPosition position = new PortfolioPosition();
        position.setId(10L);
        position.setPortfolioId(1L);
        position.setInstrumentId(20L);
        position.setTotalQuantity(new BigDecimal("100.000000"));
        position.setAvailableQuantity(new BigDecimal("100.000000"));
        position.setFrozenQuantity(BigDecimal.ZERO);
        position.setAverageCost(new BigDecimal("12.3456"));
        return position;
    }
}
