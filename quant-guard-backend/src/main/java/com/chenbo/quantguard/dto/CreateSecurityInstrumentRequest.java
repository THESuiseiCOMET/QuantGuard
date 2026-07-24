package com.chenbo.quantguard.dto;

import com.chenbo.quantguard.enums.AssetType;
import com.chenbo.quantguard.enums.Exchange;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateSecurityInstrumentRequest(
        @NotBlank(message = "symbol is required")
        @Size(max = 32, message = "symbol must be at most 32 characters")
        String symbol,

        @NotNull(message = "exchange is required")
        Exchange exchange,

        @NotBlank(message = "instrumentName is required")
        @Size(max = 128, message = "instrumentName must be at most 128 characters")
        String instrumentName,

        @NotNull(message = "assetType is required")
        AssetType assetType,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be 3 characters")
        String currency,

        @NotNull(message = "lotSize is required")
        @DecimalMin(value = "0.000001", message = "lotSize must be greater than 0")
        @Digits(integer = 13, fraction = 6, message = "lotSize supports up to 13 integer digits and 6 decimal places")
        BigDecimal lotSize
) {
}
