package com.vanyiung.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vanyiung.quantguard.dto.CreateSimulationAccountRequest;
import com.vanyiung.quantguard.dto.SimulationAccountResponse;
import com.vanyiung.quantguard.entity.SimulationAccount;
import com.vanyiung.quantguard.enums.AccountStatus;
import com.vanyiung.quantguard.exception.DuplicateResourceException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
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

    public SimulationAccountServiceImpl(SimulationAccountMapper simulationAccountMapper) {
        this.simulationAccountMapper = simulationAccountMapper;
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
        SimulationAccount account = simulationAccountMapper.selectById(id);
        if (account == null) {
            throw new ResourceNotFoundException("Simulation account not found");
        }

        return SimulationAccountResponse.from(account);
    }
}
