package com.chenbo.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chenbo.quantguard.dto.SimulatedTradeResponse;
import com.chenbo.quantguard.entity.InvestmentPortfolio;
import com.chenbo.quantguard.entity.SimulatedOrder;
import com.chenbo.quantguard.entity.SimulatedTrade;
import com.chenbo.quantguard.exception.ResourceNotFoundException;
import com.chenbo.quantguard.mapper.InvestmentPortfolioMapper;
import com.chenbo.quantguard.mapper.SimulatedOrderMapper;
import com.chenbo.quantguard.mapper.SimulatedTradeMapper;
import com.chenbo.quantguard.service.SimulatedTradeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SimulatedTradeServiceImpl implements SimulatedTradeService {

    private final SimulatedTradeMapper simulatedTradeMapper;
    private final SimulatedOrderMapper simulatedOrderMapper;
    private final InvestmentPortfolioMapper investmentPortfolioMapper;

    public SimulatedTradeServiceImpl(SimulatedTradeMapper simulatedTradeMapper,
                                     SimulatedOrderMapper simulatedOrderMapper,
                                     InvestmentPortfolioMapper investmentPortfolioMapper) {
        this.simulatedTradeMapper = simulatedTradeMapper;
        this.simulatedOrderMapper = simulatedOrderMapper;
        this.investmentPortfolioMapper = investmentPortfolioMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulatedTradeResponse> listAll() {
        return simulatedTradeMapper.selectList(Wrappers.<SimulatedTrade>lambdaQuery()
                        .orderByDesc(SimulatedTrade::getId))
                .stream()
                .map(SimulatedTradeResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SimulatedTradeResponse getById(Long id) {
        SimulatedTrade trade = simulatedTradeMapper.selectById(id);
        if (trade == null) {
            throw new ResourceNotFoundException("Simulated trade not found");
        }
        return SimulatedTradeResponse.from(trade);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulatedTradeResponse> listByOrderId(Long orderId) {
        SimulatedOrder order = simulatedOrderMapper.selectById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Simulated order not found");
        }

        return simulatedTradeMapper.selectList(Wrappers.<SimulatedTrade>lambdaQuery()
                        .eq(SimulatedTrade::getOrderId, orderId)
                        .orderByDesc(SimulatedTrade::getId))
                .stream()
                .map(SimulatedTradeResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulatedTradeResponse> listByPortfolioId(Long portfolioId) {
        InvestmentPortfolio portfolio = investmentPortfolioMapper.selectById(portfolioId);
        if (portfolio == null) {
            throw new ResourceNotFoundException("Investment portfolio not found");
        }

        return simulatedTradeMapper.selectList(Wrappers.<SimulatedTrade>lambdaQuery()
                        .eq(SimulatedTrade::getPortfolioId, portfolioId)
                        .orderByDesc(SimulatedTrade::getId))
                .stream()
                .map(SimulatedTradeResponse::from)
                .toList();
    }
}
