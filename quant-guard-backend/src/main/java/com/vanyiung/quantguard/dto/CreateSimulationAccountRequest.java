package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateSimulationAccountRequest(
        @NotBlank(message = "accountName is required")
        @Size(max = 64, message = "accountName must be at most 64 characters")
        String accountName,

        @NotNull(message = "accountType is required")
        AccountType accountType,

        @NotNull(message = "initialCash is required")
        @DecimalMin(value = "0.00", message = "initialCash must be greater than or equal to 0")
        @Digits(integer = 15, fraction = 2, message = "initialCash supports up to 15 integer digits and 2 decimal places")
        BigDecimal initialCash
) {
}
