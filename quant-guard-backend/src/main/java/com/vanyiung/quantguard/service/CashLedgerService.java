package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CashLedgerResponse;

import java.util.List;

public interface CashLedgerService {

    List<CashLedgerResponse> listByAccountId(Long accountId);
}
