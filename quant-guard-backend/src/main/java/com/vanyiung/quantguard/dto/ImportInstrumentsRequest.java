package com.vanyiung.quantguard.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

public record ImportInstrumentsRequest(
        @NotEmpty(message = "codes must not be empty")
        List<String> codes,
        boolean syncDailyPrices,
        LocalDate startDate
) {
}
