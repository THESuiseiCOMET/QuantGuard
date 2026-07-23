package com.chenbo.quantguard.dto;

import com.chenbo.quantguard.enums.OrderSide;
import com.chenbo.quantguard.enums.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateSimulatedOrderRequest(
        @NotNull(message = "accountId is required")
        @Positive(message = "accountId must be positive")
        Long accountId,

        @NotNull(message = "portfolioId is required")
        @Positive(message = "portfolioId must be positive")
        Long portfolioId,

        @NotNull(message = "instrumentId is required")
        @Positive(message = "instrumentId must be positive")
        Long instrumentId,

        @NotNull(message = "side is required")
        OrderSide side,

        @NotNull(message = "orderType is required")
        OrderType orderType,

        @NotNull(message = "requestedQuantity is required")
        @DecimalMin(value = "0.000001", message = "requestedQuantity must be greater than 0")
        @Digits(integer = 13, fraction = 6, message = "requestedQuantity supports up to 13 integer digits and 6 decimal places")
        BigDecimal requestedQuantity,

        @NotNull(message = "limitPrice is required")
        @DecimalMin(value = "0.0001", message = "limitPrice must be greater than 0")
        @Digits(integer = 15, fraction = 4, message = "limitPrice supports up to 15 integer digits and 4 decimal places")
        BigDecimal limitPrice
) {
}
