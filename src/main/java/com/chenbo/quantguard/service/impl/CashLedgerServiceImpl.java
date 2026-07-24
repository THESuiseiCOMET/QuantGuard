package com.chenbo.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chenbo.quantguard.dto.CashLedgerResponse;
import com.chenbo.quantguard.entity.CashLedger;
import com.chenbo.quantguard.entity.SimulationAccount;
import com.chenbo.quantguard.exception.ResourceNotFoundException;
import com.chenbo.quantguard.mapper.CashLedgerMapper;
import com.chenbo.quantguard.mapper.SimulationAccountMapper;
import com.chenbo.quantguard.service.CashLedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CashLedgerServiceImpl implements CashLedgerService {

    private final CashLedgerMapper cashLedgerMapper;
    private final SimulationAccountMapper simulationAccountMapper;

    public CashLedgerServiceImpl(CashLedgerMapper cashLedgerMapper, SimulationAccountMapper simulationAccountMapper) {
        this.cashLedgerMapper = cashLedgerMapper;
        this.simulationAccountMapper = simulationAccountMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashLedgerResponse> listByAccountId(Long accountId) {
        SimulationAccount account = simulationAccountMapper.selectById(accountId);
        if (account == null) {
            throw new ResourceNotFoundException("Simulation account not found");
        }

        return cashLedgerMapper.selectList(Wrappers.<CashLedger>lambdaQuery()
                        .eq(CashLedger::getAccountId, accountId)
                        .orderByDesc(CashLedger::getId))
                .stream()
                .map(CashLedgerResponse::from)
                .toList();
    }
}
