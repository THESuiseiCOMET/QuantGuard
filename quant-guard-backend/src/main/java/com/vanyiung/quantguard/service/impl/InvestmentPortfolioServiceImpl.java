package com.vanyiung.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vanyiung.quantguard.dto.CreateInvestmentPortfolioRequest;
import com.vanyiung.quantguard.dto.InvestmentPortfolioResponse;
import com.vanyiung.quantguard.entity.CashLedger;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
import com.vanyiung.quantguard.entity.PortfolioPosition;
import com.vanyiung.quantguard.entity.SimulatedOrder;
import com.vanyiung.quantguard.entity.SimulatedTrade;
import com.vanyiung.quantguard.entity.SimulationAccount;
import com.vanyiung.quantguard.enums.AccountStatus;
import com.vanyiung.quantguard.enums.OrderStatus;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.DuplicateResourceException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.CashLedgerMapper;
import com.vanyiung.quantguard.mapper.InvestmentPortfolioMapper;
import com.vanyiung.quantguard.mapper.PortfolioPositionMapper;
import com.vanyiung.quantguard.mapper.SimulatedOrderMapper;
import com.vanyiung.quantguard.mapper.SimulatedTradeMapper;
import com.vanyiung.quantguard.mapper.SimulationAccountMapper;
import com.vanyiung.quantguard.service.InvestmentPortfolioService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvestmentPortfolioServiceImpl implements InvestmentPortfolioService {

    private final InvestmentPortfolioMapper investmentPortfolioMapper;
    private final SimulationAccountMapper simulationAccountMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final SimulatedOrderMapper simulatedOrderMapper;
    private final SimulatedTradeMapper simulatedTradeMapper;
    private final CashLedgerMapper cashLedgerMapper;
    private final JdbcTemplate jdbcTemplate;

    public InvestmentPortfolioServiceImpl(InvestmentPortfolioMapper investmentPortfolioMapper,
                                          SimulationAccountMapper simulationAccountMapper,
                                          PortfolioPositionMapper portfolioPositionMapper,
                                          SimulatedOrderMapper simulatedOrderMapper,
                                          SimulatedTradeMapper simulatedTradeMapper,
                                          CashLedgerMapper cashLedgerMapper,
                                          JdbcTemplate jdbcTemplate) {
        this.investmentPortfolioMapper = investmentPortfolioMapper;
        this.simulationAccountMapper = simulationAccountMapper;
        this.portfolioPositionMapper = portfolioPositionMapper;
        this.simulatedOrderMapper = simulatedOrderMapper;
        this.simulatedTradeMapper = simulatedTradeMapper;
        this.cashLedgerMapper = cashLedgerMapper;
        this.jdbcTemplate = jdbcTemplate;
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
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Simulation account is not active");
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
        InvestmentPortfolio portfolio = requirePortfolio(id);
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

    @Override
    @Transactional
    public InvestmentPortfolioResponse updateStatus(Long id, AccountStatus status) {
        InvestmentPortfolio portfolio = requirePortfolio(id);
        if (portfolio.getStatus() == status) {
            return InvestmentPortfolioResponse.from(portfolio);
        }
        if (status == AccountStatus.DISABLED) {
            ensurePortfolioCanBeDisabled(portfolio.getId());
        }
        portfolio.setStatus(status);
        portfolio.setUpdatedAt(LocalDateTime.now());
        int updatedRows = investmentPortfolioMapper.updateById(portfolio);
        if (updatedRows != 1) {
            throw new BusinessRuleException("Investment portfolio changed, please retry");
        }
        return InvestmentPortfolioResponse.from(portfolio);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        InvestmentPortfolio portfolio = requirePortfolio(id);
        ensurePortfolioCanBeDeleted(portfolio.getId());
        int deletedRows = investmentPortfolioMapper.deleteById(id);
        if (deletedRows != 1) {
            throw new BusinessRuleException("Investment portfolio deletion failed, please retry");
        }
    }

    @Override
    @Transactional
    public void forceDelete(Long id) {
        requirePortfolio(id);
        deletePortfolioSimulationData(id);
        int deletedRows = investmentPortfolioMapper.deleteById(id);
        if (deletedRows != 1) {
            throw new BusinessRuleException("Investment portfolio deletion failed, please retry");
        }
    }

    private InvestmentPortfolio requirePortfolio(Long id) {
        InvestmentPortfolio portfolio = investmentPortfolioMapper.selectById(id);
        if (portfolio == null) {
            throw new ResourceNotFoundException("Investment portfolio not found");
        }
        return portfolio;
    }

    private void ensurePortfolioCanBeDisabled(Long portfolioId) {
        if (simulatedOrderMapper.exists(Wrappers.<SimulatedOrder>lambdaQuery()
                .eq(SimulatedOrder::getPortfolioId, portfolioId)
                .eq(SimulatedOrder::getStatus, OrderStatus.OPEN))) {
            throw new BusinessRuleException("Portfolio has open orders and cannot be disabled");
        }
        if (portfolioPositionMapper.exists(Wrappers.<PortfolioPosition>lambdaQuery()
                .eq(PortfolioPosition::getPortfolioId, portfolioId)
                .gt(PortfolioPosition::getFrozenQuantity, BigDecimal.ZERO))) {
            throw new BusinessRuleException("Portfolio has frozen positions and cannot be disabled");
        }
    }

    private void ensurePortfolioCanBeDeleted(Long portfolioId) {
        boolean hasRelatedData = portfolioPositionMapper.exists(Wrappers.<PortfolioPosition>lambdaQuery()
                .eq(PortfolioPosition::getPortfolioId, portfolioId))
                || simulatedOrderMapper.exists(Wrappers.<SimulatedOrder>lambdaQuery()
                .eq(SimulatedOrder::getPortfolioId, portfolioId))
                || simulatedTradeMapper.exists(Wrappers.<SimulatedTrade>lambdaQuery()
                .eq(SimulatedTrade::getPortfolioId, portfolioId))
                || cashLedgerMapper.exists(Wrappers.<CashLedger>lambdaQuery()
                .eq(CashLedger::getPortfolioId, portfolioId));
        if (hasRelatedData) {
            throw new BusinessRuleException(
                    "Investment portfolio has related business data and cannot be deleted. Disable it instead.");
        }
        deleteLegacyRiskConfig(portfolioId);
    }

    private void deletePortfolioSimulationData(Long portfolioId) {
        cashLedgerMapper.delete(Wrappers.<CashLedger>lambdaQuery()
                .eq(CashLedger::getPortfolioId, portfolioId));
        simulatedTradeMapper.delete(Wrappers.<SimulatedTrade>lambdaQuery()
                .eq(SimulatedTrade::getPortfolioId, portfolioId));
        simulatedOrderMapper.delete(Wrappers.<SimulatedOrder>lambdaQuery()
                .eq(SimulatedOrder::getPortfolioId, portfolioId));
        portfolioPositionMapper.delete(Wrappers.<PortfolioPosition>lambdaQuery()
                .eq(PortfolioPosition::getPortfolioId, portfolioId));
        deleteLegacyRiskConfig(portfolioId);
    }

    private void deleteLegacyRiskConfig(Long portfolioId) {
        jdbcTemplate.update("DELETE FROM portfolio_risk_config WHERE portfolio_id = ?", portfolioId);
    }
}
