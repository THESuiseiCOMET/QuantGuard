package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.enums.AssetType;
import com.vanyiung.quantguard.enums.Exchange;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstrumentImportPreviewItem(
        String originalInput,
        String canonicalCode,
        String symbol,
        Exchange exchange,
        String instrumentName,
        String currency,
        AssetType assetType,
        BigDecimal lotSize,
        String listStatus,
        LocalDate listDate,
        boolean alreadyExists,
        Long existingInstrumentId,
        boolean valid,
        String message
) {
}
