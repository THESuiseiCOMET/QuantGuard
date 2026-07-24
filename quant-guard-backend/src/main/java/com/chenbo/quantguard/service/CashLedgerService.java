package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.CashLedgerResponse;

import java.util.List;

public interface CashLedgerService {

    List<CashLedgerResponse> listByAccountId(Long accountId);
}
