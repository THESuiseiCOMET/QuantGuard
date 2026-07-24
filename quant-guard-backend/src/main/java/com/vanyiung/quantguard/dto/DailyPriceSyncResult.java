package com.vanyiung.quantguard.dto;

import java.time.LocalDate;

public record DailyPriceSyncResult(
        Long instrumentId,
        String canonicalCode,
        String status,
        int recordsReceived,
        int recordsInserted,
        int recordsUpdated,
        LocalDate latestTradeDate,
        String message
) {
}
