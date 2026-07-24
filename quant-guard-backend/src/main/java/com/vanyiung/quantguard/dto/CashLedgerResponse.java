package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.entity.CashLedger;
import com.vanyiung.quantguard.enums.CashLedgerEntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashLedgerResponse(
        Long id,
        Long accountId,
        Long portfolioId,
        Long orderId,
        Long tradeId,
        CashLedgerEntryType entryType,
        BigDecimal availableCashDelta,
        BigDecimal frozenCashDelta,
        BigDecimal availableCashAfter,
        BigDecimal frozenCashAfter,
        String description,
        LocalDateTime createdAt
) {

    public static CashLedgerResponse from(CashLedger ledger) {
        return new CashLedgerResponse(
                ledger.getId(),
                ledger.getAccountId(),
                ledger.getPortfolioId(),
                ledger.getOrderId(),
                ledger.getTradeId(),
                ledger.getEntryType(),
                ledger.getAvailableCashDelta(),
                ledger.getFrozenCashDelta(),
                ledger.getAvailableCashAfter(),
                ledger.getFrozenCashAfter(),
                ledger.getDescription(),
                ledger.getCreatedAt()
        );
    }
}
