package com.vanyiung.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vanyiung.quantguard.dto.PortfolioPositionResponse;
import com.vanyiung.quantguard.dto.UpdatePortfolioPositionRequest;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
import com.vanyiung.quantguard.entity.PortfolioPosition;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.InvestmentPortfolioMapper;
import com.vanyiung.quantguard.mapper.PortfolioPositionMapper;
import com.vanyiung.quantguard.service.PortfolioPositionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Override
    @Transactional
    public PortfolioPositionResponse update(Long id, UpdatePortfolioPositionRequest request) {
        PortfolioPosition position = portfolioPositionMapper.selectById(id);
        if (position == null) {
            throw new ResourceNotFoundException("Portfolio position not found");
        }
        if (request.availableQuantity().compareTo(request.totalQuantity()) > 0) {
            throw new BusinessRuleException("Available quantity cannot exceed total quantity");
        }
        if (position.getFrozenQuantity().compareTo(request.totalQuantity()) > 0) {
            throw new BusinessRuleException("Total quantity cannot be less than frozen quantity");
        }

        position.setTotalQuantity(request.totalQuantity());
        position.setAvailableQuantity(request.availableQuantity());
        position.setAverageCost(request.averageCost());
        position.setUpdatedAt(LocalDateTime.now());
        int updatedRows = portfolioPositionMapper.updateById(position);
        if (updatedRows != 1) {
            throw new BusinessRuleException("Portfolio position changed, please retry");
        }
        return PortfolioPositionResponse.from(position);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PortfolioPosition position = portfolioPositionMapper.selectById(id);
        if (position == null) {
            throw new ResourceNotFoundException("Portfolio position not found");
        }
        if (position.getFrozenQuantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException("Portfolio position has frozen quantity and cannot be deleted");
        }
        if (position.getTotalQuantity().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleException(
                    "Portfolio position still has quantity and cannot be deleted. Close the position through sell orders first.");
        }
        int deletedRows = portfolioPositionMapper.deleteById(id);
        if (deletedRows != 1) {
            throw new BusinessRuleException("Portfolio position deletion failed, please retry");
        }
    }
}
