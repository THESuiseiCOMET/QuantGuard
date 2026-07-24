package com.vanyiung.quantguard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyPriceSummaryResponse(
        Long instrumentId,
        LocalDate firstTradeDate,
        LocalDate latestTradeDate,
        long recordCount,
        BigDecimal latestClose,
        BigDecimal latestPercentageChange,
        BigDecimal periodHigh,
        BigDecimal periodLow,
        LocalDateTime lastSyncAt,
        String dataSource
) {
}
