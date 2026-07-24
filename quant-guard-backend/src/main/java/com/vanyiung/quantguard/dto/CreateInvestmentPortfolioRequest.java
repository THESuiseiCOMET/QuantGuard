package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateInvestmentPortfolioRequest(
        @NotNull(message = "accountId is required")
        @Positive(message = "accountId must be positive")
        Long accountId,

        @NotBlank(message = "portfolioName is required")
        @Size(max = 64, message = "portfolioName must be at most 64 characters")
        String portfolioName,

        @NotNull(message = "portfolioType is required")
        AccountType portfolioType,

        @Size(max = 255, message = "description must be at most 255 characters")
        String description
) {
}
