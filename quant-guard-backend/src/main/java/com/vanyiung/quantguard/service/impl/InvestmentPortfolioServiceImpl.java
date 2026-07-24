package com.vanyiung.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vanyiung.quantguard.dto.CreateInvestmentPortfolioRequest;
import com.vanyiung.quantguard.dto.InvestmentPortfolioResponse;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
import com.vanyiung.quantguard.entity.SimulationAccount;
import com.vanyiung.quantguard.enums.AccountStatus;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.DuplicateResourceException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.InvestmentPortfolioMapper;
import com.vanyiung.quantguard.mapper.SimulationAccountMapper;
import com.vanyiung.quantguard.service.InvestmentPortfolioService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvestmentPortfolioServiceImpl implements InvestmentPortfolioService {

    private final InvestmentPortfolioMapper investmentPortfolioMapper;
    private final SimulationAccountMapper simulationAccountMapper;

    public InvestmentPortfolioServiceImpl(InvestmentPortfolioMapper investmentPortfolioMapper,
                                          SimulationAccountMapper simulationAccountMapper) {
        this.investmentPortfolioMapper = investmentPortfolioMapper;
        this.simulationAccountMapper = simulationAccountMapper;
    }

    @Override
    @Transactional
    public InvestmentPortfolioResponse create(CreateInvestmentPortfolioRequest request) {
        String portfolioName = request.portfolioName().trim();
        String description = request.description() == null ? null : request.description().trim();
        SimulationAccount account = simulationAccountMapper.selectById(request.accountId());
        if (account == null) {
            throw new ResourceNotFoundException("Simulation account not found");
        }
        if (account.getAccountType() != request.portfolioType()) {
            throw new BusinessRuleException("Portfolio type must match simulation account type");
        }

        boolean nameExists = investmentPortfolioMapper.exists(Wrappers.<InvestmentPortfolio>lambdaQuery()
                .eq(InvestmentPortfolio::getAccountId, request.accountId())
                .eq(InvestmentPortfolio::getPortfolioName, portfolioName));
        if (nameExists) {
            throw new DuplicateResourceException("Portfolio name already exists in this account");
        }

        LocalDateTime now = LocalDateTime.now();
        InvestmentPortfolio portfolio = new InvestmentPortfolio();
        portfolio.setAccountId(request.accountId());
        portfolio.setPortfolioName(portfolioName);
        portfolio.setPortfolioType(request.portfolioType());
        portfolio.setDescription(description);
        portfolio.setStatus(AccountStatus.ACTIVE);
        portfolio.setCreatedAt(now);
        portfolio.setUpdatedAt(now);

        try {
            investmentPortfolioMapper.insert(portfolio);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateResourceException("Portfolio name already exists in this account");
        }

        return InvestmentPortfolioResponse.from(portfolio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvestmentPortfolioResponse> listAll() {
        return investmentPortfolioMapper.selectList(Wrappers.<InvestmentPortfolio>lambdaQuery()
                        .orderByDesc(InvestmentPortfolio::getId))
                .stream()
                .map(InvestmentPortfolioResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InvestmentPortfolioResponse getById(Long id) {
        InvestmentPortfolio portfolio = investmentPortfolioMapper.selectById(id);
        if (portfolio == null) {
            throw new ResourceNotFoundException("Investment portfolio not found");
        }

        return InvestmentPortfolioResponse.from(portfolio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvestmentPortfolioResponse> listByAccountId(Long accountId) {
        SimulationAccount account = simulationAccountMapper.selectById(accountId);
        if (account == null) {
            throw new ResourceNotFoundException("Simulation account not found");
        }

        return investmentPortfolioMapper.selectList(Wrappers.<InvestmentPortfolio>lambdaQuery()
                        .eq(InvestmentPortfolio::getAccountId, accountId)
                        .orderByDesc(InvestmentPortfolio::getId))
                .stream()
                .map(InvestmentPortfolioResponse::from)
                .toList();
    }
}
