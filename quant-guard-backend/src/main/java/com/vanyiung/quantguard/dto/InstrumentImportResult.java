package com.vanyiung.quantguard.dto;

import java.time.LocalDate;

public record InstrumentImportResult(
        String canonicalCode,
        Long instrumentId,
        String instrumentName,
        String action,
        String metadataStatus,
        String priceSyncStatus,
        int recordsInserted,
        int recordsUpdated,
        LocalDate latestTradeDate,
        String message
) {
}
