package com.vanyiung.quantguard.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreatePortfolioRiskConfigRequest(
        @NotNull(message = "portfolioId is required")
        @Positive(message = "portfolioId must be positive")
        Long portfolioId,

        @NotNull(message = "maxSingleOrderAmount is required")
        @DecimalMin(value = "0.01", message = "maxSingleOrderAmount must be greater than 0")
        @Digits(integer = 15, fraction = 4, message = "maxSingleOrderAmount supports up to 15 integer digits and 4 decimal places")
        BigDecimal maxSingleOrderAmount,

        @NotNull(message = "maxPositionRatio is required")
        @DecimalMin(value = "0.000000", message = "maxPositionRatio must be greater than or equal to 0")
        @DecimalMax(value = "1.000000", message = "maxPositionRatio must be less than or equal to 1")
        @Digits(integer = 1, fraction = 6, message = "maxPositionRatio supports up to 6 decimal places")
        BigDecimal maxPositionRatio,

        @NotNull(message = "allowBuy is required")
        Boolean allowBuy,

        @NotNull(message = "allowSell is required")
        Boolean allowSell
) {
}
