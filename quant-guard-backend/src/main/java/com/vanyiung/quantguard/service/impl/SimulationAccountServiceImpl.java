package com.vanyiung.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vanyiung.quantguard.dto.CreateSimulationAccountRequest;
import com.vanyiung.quantguard.dto.SimulationAccountResponse;
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
import com.vanyiung.quantguard.service.SimulationAccountService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SimulationAccountServiceImpl implements SimulationAccountService {

    private final SimulationAccountMapper simulationAccountMapper;
    private final InvestmentPortfolioMapper investmentPortfolioMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final SimulatedOrderMapper simulatedOrderMapper;
    private final SimulatedTradeMapper simulatedTradeMapper;
    private final CashLedgerMapper cashLedgerMapper;
    private final JdbcTemplate jdbcTemplate;

    public SimulationAccountServiceImpl(SimulationAccountMapper simulationAccountMapper,
                                        InvestmentPortfolioMapper investmentPortfolioMapper,
                                        PortfolioPositionMapper portfolioPositionMapper,
                                        SimulatedOrderMapper simulatedOrderMapper,
                                        SimulatedTradeMapper simulatedTradeMapper,
                                        CashLedgerMapper cashLedgerMapper,
                                        JdbcTemplate jdbcTemplate) {
        this.simulationAccountMapper = simulationAccountMapper;
        this.investmentPortfolioMapper = investmentPortfolioMapper;
        this.portfolioPositionMapper = portfolioPositionMapper;
        this.simulatedOrderMapper = simulatedOrderMapper;
        this.simulatedTradeMapper = simulatedTradeMapper;
        this.cashLedgerMapper = cashLedgerMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public SimulationAccountResponse create(CreateSimulationAccountRequest request) {
        String accountName = request.accountName().trim();
        boolean nameExists = simulationAccountMapper.exists(Wrappers.<SimulationAccount>lambdaQuery()
                .eq(SimulationAccount::getAccountName, accountName));
        if (nameExists) {
            throw new DuplicateResourceException("Simulation account name already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        SimulationAccount account = new SimulationAccount();
        account.setAccountName(accountName);
        account.setAccountType(request.accountType());
        account.setInitialCash(request.initialCash());
        account.setAvailableCash(request.initialCash());
        account.setFrozenCash(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setVersion(0);

        try {
            simulationAccountMapper.insert(account);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateResourceException("Simulation account name already exists");
        }

        createDefaultPortfolio(account, now);
        return SimulationAccountResponse.from(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulationAccountResponse> listAll() {
        return simulationAccountMapper.selectList(Wrappers.<SimulationAccount>lambdaQuery()
                        .orderByDesc(SimulationAccount::getId))
                .stream()
                .map(SimulationAccountResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SimulationAccountResponse getById(Long id) {
        SimulationAccount account = requireAccount(id);
        return SimulationAccountResponse.from(account);
    }

    @Override
    @Transactional
    public SimulationAccountResponse updateStatus(Long id, AccountStatus status) {
        SimulationAccount account = requireAccount(id);
        if (account.getStatus() == status) {
            return SimulationAccountResponse.from(account);
        }
        if (status == AccountStatus.DISABLED) {
            ensureAccountCanBeDisabled(account);
        }
        account.setStatus(status);
        account.setUpdatedAt(LocalDateTime.now());
        int updatedRows = simulationAccountMapper.updateById(account);
        if (updatedRows != 1) {
            throw new BusinessRuleException("Simulation account changed, please retry");
        }
        return SimulationAccountResponse.from(account);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SimulationAccount account = requireAccount(id);
        List<Long> portfolioIds = ensureAccountCanBeDeleted(account);
        if (!portfolioIds.isEmpty()) {
            deleteLegacyRiskConfigs(portfolioIds);
            investmentPortfolioMapper.delete(Wrappers.<InvestmentPortfolio>lambdaQuery()
                    .eq(InvestmentPortfolio::getAccountId, id));
        }
        int deletedRows = simulationAccountMapper.deleteById(id);
        if (deletedRows != 1) {
            throw new BusinessRuleException("Simulation account deletion failed, please retry");
        }
    }

    @Override
    @Transactional
    public void forceDelete(Long id) {
        requireAccount(id);
        List<Long> portfolioIds = investmentPortfolioMapper.selectList(Wrappers.<InvestmentPortfolio>lambdaQuery()
                        .eq(InvestmentPortfolio::getAccountId, id))
                .stream()
                .map(InvestmentPortfolio::getId)
                .toList();

        cashLedgerMapper.delete(Wrappers.<CashLedger>lambdaQuery()
                .eq(CashLedger::getAccountId, id));
        simulatedTradeMapper.delete(Wrappers.<SimulatedTrade>lambdaQuery()
                .eq(SimulatedTrade::getAccountId, id));
        simulatedOrderMapper.delete(Wrappers.<SimulatedOrder>lambdaQuery()
                .eq(SimulatedOrder::getAccountId, id));

        if (!portfolioIds.isEmpty()) {
            portfolioPositionMapper.delete(Wrappers.<PortfolioPosition>query()
                    .in("portfolio_id", portfolioIds));
            deleteLegacyRiskConfigs(portfolioIds);
            investmentPortfolioMapper.delete(Wrappers.<InvestmentPortfolio>lambdaQuery()
                    .eq(InvestmentPortfolio::getAccountId, id));
        }

        int deletedRows = simulationAccountMapper.deleteById(id);
        if (deletedRows != 1) {
            throw new BusinessRuleException("Simulation account deletion failed, please retry");
        }
    }

    private SimulationAccount requireAccount(Long id) {
        SimulationAccount account = simulationAccountMapper.selectById(id);
        if (account == null) {
            throw new ResourceNotFoundException("Simulation account not found");
        }
        return account;
    }

    private void createDefaultPortfolio(SimulationAccount account, LocalDateTime now) {
        InvestmentPortfolio portfolio = new InvestmentPortfolio();
        portfolio.setAccountId(account.getId());
        portfolio.setPortfolioName("默认组合");
        portfolio.setPortfolioType(account.getAccountType());
        portfolio.setDescription(null);
        portfolio.setStatus(AccountStatus.ACTIVE);
        portfolio.setCreatedAt(now);
        portfolio.setUpdatedAt(now);

        int insertedRows = investmentPortfolioMapper.insert(portfolio);
        if (insertedRows != 1) {
            throw new BusinessRuleException("Default investment portfolio creation failed");
        }
    }

    private void ensureAccountCanBeDisabled(SimulationAccount account) {
        if (account.getFrozenCash().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException("Account has frozen cash and cannot be disabled");
        }
        if (simulatedOrderMapper.exists(Wrappers.<SimulatedOrder>lambdaQuery()
                .eq(SimulatedOrder::getAccountId, account.getId())
                .eq(SimulatedOrder::getStatus, OrderStatus.OPEN))) {
            throw new BusinessRuleException("Account has open orders and cannot be disabled");
        }
    }

    private List<Long> ensureAccountCanBeDeleted(SimulationAccount account) {
        List<Long> portfolioIds = investmentPortfolioMapper.selectList(Wrappers.<InvestmentPortfolio>lambdaQuery()
                        .eq(InvestmentPortfolio::getAccountId, account.getId()))
                .stream()
                .map(InvestmentPortfolio::getId)
                .toList();
        boolean hasRelatedData = simulatedOrderMapper.exists(Wrappers.<SimulatedOrder>lambdaQuery()
                .eq(SimulatedOrder::getAccountId, account.getId()))
                || simulatedTradeMapper.exists(Wrappers.<SimulatedTrade>lambdaQuery()
                .eq(SimulatedTrade::getAccountId, account.getId()))
                || cashLedgerMapper.exists(Wrappers.<CashLedger>lambdaQuery()
                .eq(CashLedger::getAccountId, account.getId()))
                || (!portfolioIds.isEmpty() && portfolioPositionMapper.exists(Wrappers.<PortfolioPosition>query()
                .in("portfolio_id", portfolioIds)));
        boolean cashUntouched = account.getFrozenCash().compareTo(BigDecimal.ZERO) == 0
                && account.getAvailableCash().compareTo(account.getInitialCash()) == 0;
        if (hasRelatedData || !cashUntouched) {
            throw new BusinessRuleException(
                    "Simulation account has related business data and cannot be deleted. Disable it instead.");
        }
        return portfolioIds;
    }

    private void deleteLegacyRiskConfigs(List<Long> portfolioIds) {
        for (Long portfolioId : portfolioIds) {
            jdbcTemplate.update("DELETE FROM portfolio_risk_config WHERE portfolio_id = ?", portfolioId);
        }
    }
}
