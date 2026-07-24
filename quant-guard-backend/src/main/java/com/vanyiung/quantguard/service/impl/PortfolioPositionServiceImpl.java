package com.vanyiung.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vanyiung.quantguard.dto.PortfolioPositionResponse;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
import com.vanyiung.quantguard.entity.PortfolioPosition;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.InvestmentPortfolioMapper;
import com.vanyiung.quantguard.mapper.PortfolioPositionMapper;
import com.vanyiung.quantguard.service.PortfolioPositionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioPositionServiceImpl implements PortfolioPositionService {

    private final PortfolioPositionMapper portfolioPositionMapper;
    private final InvestmentPortfolioMapper investmentPortfolioMapper;

    public PortfolioPositionServiceImpl(PortfolioPositionMapper portfolioPositionMapper,
                                        InvestmentPortfolioMapper investmentPortfolioMapper) {
        this.portfolioPositionMapper = portfolioPositionMapper;
        this.investmentPortfolioMapper = investmentPortfolioMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioPositionResponse> listByPortfolioId(Long portfolioId) {
        InvestmentPortfolio portfolio = investmentPortfolioMapper.selectById(portfolioId);
        if (portfolio == null) {
            throw new ResourceNotFoundException("Investment portfolio not found");
        }

        return portfolioPositionMapper.selectList(Wrappers.<PortfolioPosition>lambdaQuery()
                        .eq(PortfolioPosition::getPortfolioId, portfolioId)
                        .orderByAsc(PortfolioPosition::getInstrumentId))
                .stream()
                .map(PortfolioPositionResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioPositionResponse getById(Long id) {
        PortfolioPosition position = portfolioPositionMapper.selectById(id);
        if (position == null) {
            throw new ResourceNotFoundException("Portfolio position not found");
        }

        return PortfolioPositionResponse.from(position);
    }
}
