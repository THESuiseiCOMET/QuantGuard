package com.vanyiung.quantguard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdatePortfolioPositionRequest(
        @NotNull(message = "totalQuantity is required")
        @DecimalMin(value = "0.0000", message = "totalQuantity must be greater than or equal to 0")
        @Digits(integer = 15, fraction = 4, message = "totalQuantity supports up to 15 integer digits and 4 decimal places")
        BigDecimal totalQuantity,

        @NotNull(message = "availableQuantity is required")
        @DecimalMin(value = "0.0000", message = "availableQuantity must be greater than or equal to 0")
        @Digits(integer = 15, fraction = 4, message = "availableQuantity supports up to 15 integer digits and 4 decimal places")
        BigDecimal availableQuantity,

        @NotNull(message = "averageCost is required")
        @DecimalMin(value = "0.0000", message = "averageCost must be greater than or equal to 0")
        @Digits(integer = 15, fraction = 4, message = "averageCost supports up to 15 integer digits and 4 decimal places")
        BigDecimal averageCost
) {
}
