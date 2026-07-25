package com.vanyiung.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vanyiung.quantguard.dto.CreateSimulationAccountRequest;
import com.vanyiung.quantguard.dto.SimulationAccountResponse;
import com.vanyiung.quantguard.entity.CashLedger;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
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
import com.vanyiung.quantguard.mapper.SimulatedOrderMapper;
import com.vanyiung.quantguard.mapper.SimulatedTradeMapper;
import com.vanyiung.quantguard.mapper.SimulationAccountMapper;
import com.vanyiung.quantguard.service.SimulationAccountService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SimulationAccountServiceImpl implements SimulationAccountService {

    private final SimulationAccountMapper simulationAccountMapper;
    private final InvestmentPortfolioMapper investmentPortfolioMapper;
    private final SimulatedOrderMapper simulatedOrderMapper;
    private final SimulatedTradeMapper simulatedTradeMapper;
    private final CashLedgerMapper cashLedgerMapper;

    public SimulationAccountServiceImpl(SimulationAccountMapper simulationAccountMapper,
                                        InvestmentPortfolioMapper investmentPortfolioMapper,
                                        SimulatedOrderMapper simulatedOrderMapper,
                                        SimulatedTradeMapper simulatedTradeMapper,
                                        CashLedgerMapper cashLedgerMapper) {
        this.simulationAccountMapper = simulationAccountMapper;
        this.investmentPortfolioMapper = investmentPortfolioMapper;
        this.simulatedOrderMapper = simulatedOrderMapper;
        this.simulatedTradeMapper = simulatedTradeMapper;
        this.cashLedgerMapper = cashLedgerMapper;
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
        ensureAccountCanBeDeleted(account);
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

    private void ensureAccountCanBeDeleted(SimulationAccount account) {
        boolean hasRelatedData = investmentPortfolioMapper.exists(Wrappers.<InvestmentPortfolio>lambdaQuery()
                .eq(InvestmentPortfolio::getAccountId, account.getId()))
                || simulatedOrderMapper.exists(Wrappers.<SimulatedOrder>lambdaQuery()
                .eq(SimulatedOrder::getAccountId, account.getId()))
                || simulatedTradeMapper.exists(Wrappers.<SimulatedTrade>lambdaQuery()
                .eq(SimulatedTrade::getAccountId, account.getId()))
                || cashLedgerMapper.exists(Wrappers.<CashLedger>lambdaQuery()
                .eq(CashLedger::getAccountId, account.getId()));
        boolean cashUntouched = account.getFrozenCash().compareTo(BigDecimal.ZERO) == 0
                && account.getAvailableCash().compareTo(account.getInitialCash()) == 0;
        if (hasRelatedData || !cashUntouched) {
            throw new BusinessRuleException(
                    "Simulation account has related business data and cannot be deleted. Disable it instead.");
        }
    }
}
