package com.chenbo.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chenbo.quantguard.dto.CreatePortfolioRiskConfigRequest;
import com.chenbo.quantguard.dto.PortfolioRiskConfigResponse;
import com.chenbo.quantguard.dto.UpdatePortfolioRiskConfigRequest;
import com.chenbo.quantguard.entity.InvestmentPortfolio;
import com.chenbo.quantguard.entity.PortfolioRiskConfig;
import com.chenbo.quantguard.enums.AccountStatus;
import com.chenbo.quantguard.enums.RiskConfigStatus;
import com.chenbo.quantguard.exception.BusinessRuleException;
import com.chenbo.quantguard.exception.DuplicateResourceException;
import com.chenbo.quantguard.exception.ResourceNotFoundException;
import com.chenbo.quantguard.mapper.InvestmentPortfolioMapper;
import com.chenbo.quantguard.mapper.PortfolioRiskConfigMapper;
import com.chenbo.quantguard.service.PortfolioRiskConfigService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PortfolioRiskConfigServiceImpl implements PortfolioRiskConfigService {

    private final PortfolioRiskConfigMapper portfolioRiskConfigMapper;
    private final InvestmentPortfolioMapper investmentPortfolioMapper;

    public PortfolioRiskConfigServiceImpl(PortfolioRiskConfigMapper portfolioRiskConfigMapper,
                                          InvestmentPortfolioMapper investmentPortfolioMapper) {
        this.portfolioRiskConfigMapper = portfolioRiskConfigMapper;
        this.investmentPortfolioMapper = investmentPortfolioMapper;
    }

    @Override
    @Transactional
    public PortfolioRiskConfigResponse create(CreatePortfolioRiskConfigRequest request) {
        InvestmentPortfolio portfolio = investmentPortfolioMapper.selectById(request.portfolioId());
        if (portfolio == null) {
            throw new ResourceNotFoundException("Investment portfolio not found");
        }
        if (portfolio.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Investment portfolio is not active");
        }

        boolean exists = portfolioRiskConfigMapper.exists(Wrappers.<PortfolioRiskConfig>lambdaQuery()
                .eq(PortfolioRiskConfig::getPortfolioId, request.portfolioId()));
        if (exists) {
            throw new DuplicateResourceException("Portfolio risk config already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        PortfolioRiskConfig config = new PortfolioRiskConfig();
        config.setPortfolioId(request.portfolioId());
        config.setMaxSingleOrderAmount(request.maxSingleOrderAmount());
        config.setMaxPositionRatio(request.maxPositionRatio());
        config.setAllowBuy(request.allowBuy());
        config.setAllowSell(request.allowSell());
        config.setStatus(RiskConfigStatus.ACTIVE);
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        config.setVersion(0);

        try {
            portfolioRiskConfigMapper.insert(config);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateResourceException("Portfolio risk config already exists");
        }

        return PortfolioRiskConfigResponse.from(config);
    }

    @Override
    @Transactional
    public PortfolioRiskConfigResponse update(Long id, UpdatePortfolioRiskConfigRequest request) {
        PortfolioRiskConfig config = portfolioRiskConfigMapper.selectById(id);
        if (config == null) {
            throw new ResourceNotFoundException("Portfolio risk config not found");
        }

        LocalDateTime now = LocalDateTime.now();
        config.setMaxSingleOrderAmount(request.maxSingleOrderAmount());
        config.setMaxPositionRatio(request.maxPositionRatio());
        config.setAllowBuy(request.allowBuy());
        config.setAllowSell(request.allowSell());
        config.setUpdatedAt(now);

        int updatedRows = portfolioRiskConfigMapper.updateById(config);
        if (updatedRows != 1) {
            throw new BusinessRuleException("Portfolio risk config update failed");
        }

        return PortfolioRiskConfigResponse.from(config);
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioRiskConfigResponse getById(Long id) {
        PortfolioRiskConfig config = portfolioRiskConfigMapper.selectById(id);
        if (config == null) {
            throw new ResourceNotFoundException("Portfolio risk config not found");
        }

        return PortfolioRiskConfigResponse.from(config);
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioRiskConfigResponse getByPortfolioId(Long portfolioId) {
        InvestmentPortfolio portfolio = investmentPortfolioMapper.selectById(portfolioId);
        if (portfolio == null) {
            throw new ResourceNotFoundException("Investment portfolio not found");
        }

        PortfolioRiskConfig config = portfolioRiskConfigMapper.selectOne(Wrappers.<PortfolioRiskConfig>lambdaQuery()
                .eq(PortfolioRiskConfig::getPortfolioId, portfolioId));
        if (config == null) {
            throw new ResourceNotFoundException("Portfolio risk config not found");
        }

        return PortfolioRiskConfigResponse.from(config);
    }
}
