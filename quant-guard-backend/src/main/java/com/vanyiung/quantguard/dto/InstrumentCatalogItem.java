package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.enums.AssetType;
import com.vanyiung.quantguard.enums.Exchange;

import java.time.LocalDate;

public record InstrumentCatalogItem(
        String canonicalCode,
        String symbol,
        String instrumentName,
        Exchange exchange,
        AssetType assetType,
        String currency,
        String listStatus,
        LocalDate listDate,
        boolean alreadyExists,
        Long existingInstrumentId
) {
}
