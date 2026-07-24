package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreatePortfolioRiskConfigRequest;
import com.vanyiung.quantguard.dto.PortfolioRiskConfigResponse;
import com.vanyiung.quantguard.dto.UpdatePortfolioRiskConfigRequest;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
import com.vanyiung.quantguard.entity.PortfolioRiskConfig;
import com.vanyiung.quantguard.enums.AccountStatus;
import com.vanyiung.quantguard.enums.RiskConfigStatus;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.DuplicateResourceException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.InvestmentPortfolioMapper;
import com.vanyiung.quantguard.mapper.PortfolioRiskConfigMapper;
import com.vanyiung.quantguard.service.impl.PortfolioRiskConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    void updateSucceedsAndPreservesImmutableFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 24, 10, 0);
        LocalDateTime previousUpdatedAt = LocalDateTime.of(2026, 7, 24, 10, 5);
        PortfolioRiskConfig existing = riskConfig(10L, 1L, createdAt, previousUpdatedAt);
        when(portfolioRiskConfigMapper.selectById(10L)).thenReturn(existing);
        when(portfolioRiskConfigMapper.updateById(any(PortfolioRiskConfig.class))).thenReturn(1);

        PortfolioRiskConfigResponse response = portfolioRiskConfigService.update(10L, new UpdatePortfolioRiskConfigRequest(
                new BigDecimal("60000.0000"), new BigDecimal("0.400000"), false, true));

        ArgumentCaptor<PortfolioRiskConfig> captor = ArgumentCaptor.forClass(PortfolioRiskConfig.class);
        verify(portfolioRiskConfigMapper).updateById(captor.capture());
        PortfolioRiskConfig updated = captor.getValue();
        assertEquals(10L, updated.getId());
        assertEquals(1L, updated.getPortfolioId());
        assertEquals(createdAt, updated.getCreatedAt());
        assertEquals(RiskConfigStatus.ACTIVE, updated.getStatus());
        assertEquals(3, updated.getVersion());
        assertEquals(new BigDecimal("60000.0000"), updated.getMaxSingleOrderAmount());
        assertEquals(new BigDecimal("0.400000"), updated.getMaxPositionRatio());
        assertEquals(false, updated.getAllowBuy());
        assertEquals(true, updated.getAllowSell());
        assertEquals(true, updated.getUpdatedAt().isAfter(previousUpdatedAt));
        assertEquals(1L, response.portfolioId());
        assertEquals(createdAt, response.createdAt());
    }

    @Test
    void updateRejectsMissingConfig() {
        when(portfolioRiskConfigMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> portfolioRiskConfigService.update(999L, new UpdatePortfolioRiskConfigRequest(
                new BigDecimal("60000.0000"), new BigDecimal("0.400000"), false, true)));
    }

    @Test
    void updateFailureIsNotDisguisedAsSuccess() {
        PortfolioRiskConfig existing = riskConfig(10L, 1L, LocalDateTime.now().minusDays(1), LocalDateTime.now().minusHours(1));
        when(portfolioRiskConfigMapper.selectById(10L)).thenReturn(existing);
        when(portfolioRiskConfigMapper.updateById(any(PortfolioRiskConfig.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> portfolioRiskConfigService.update(10L, new UpdatePortfolioRiskConfigRequest(
                new BigDecimal("60000.0000"), new BigDecimal("0.400000"), false, true)));
    }

    @Test
    void updatePropagatesDatabaseFailures() {
        PortfolioRiskConfig existing = riskConfig(10L, 1L, LocalDateTime.now().minusDays(1), LocalDateTime.now().minusHours(1));
        when(portfolioRiskConfigMapper.selectById(10L)).thenReturn(existing);
        when(portfolioRiskConfigMapper.updateById(any(PortfolioRiskConfig.class)))
                .thenThrow(new RuntimeException("database update failed"));

        assertThrows(RuntimeException.class, () -> portfolioRiskConfigService.update(10L, new UpdatePortfolioRiskConfigRequest(
                new BigDecimal("60000.0000"), new BigDecimal("0.400000"), false, true)));
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

    private PortfolioRiskConfig riskConfig(Long id, Long portfolioId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        PortfolioRiskConfig config = new PortfolioRiskConfig();
        config.setId(id);
        config.setPortfolioId(portfolioId);
        config.setMaxSingleOrderAmount(new BigDecimal("50000.0000"));
        config.setMaxPositionRatio(new BigDecimal("0.500000"));
        config.setAllowBuy(true);
        config.setAllowSell(true);
        config.setStatus(RiskConfigStatus.ACTIVE);
        config.setCreatedAt(createdAt);
        config.setUpdatedAt(updatedAt);
        config.setVersion(3);
        return config;
    }
}
