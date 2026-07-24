package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.entity.SimulationAccount;
import com.vanyiung.quantguard.enums.AccountStatus;
import com.vanyiung.quantguard.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SimulationAccountResponse(
        Long id,
        String accountName,
        AccountType accountType,
        BigDecimal initialCash,
        BigDecimal availableCash,
        BigDecimal frozenCash,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SimulationAccountResponse from(SimulationAccount account) {
        return new SimulationAccountResponse(
                account.getId(),
                account.getAccountName(),
                account.getAccountType(),
                account.getInitialCash(),
                account.getAvailableCash(),
                account.getFrozenCash(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
