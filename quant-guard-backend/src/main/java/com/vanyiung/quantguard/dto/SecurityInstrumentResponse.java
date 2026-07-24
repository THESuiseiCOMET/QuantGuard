package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.entity.SecurityInstrument;
import com.vanyiung.quantguard.enums.AssetType;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.enums.InstrumentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SecurityInstrumentResponse(
        Long id,
        String symbol,
        Exchange exchange,
        String instrumentName,
        AssetType assetType,
        String currency,
        BigDecimal lotSize,
        InstrumentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SecurityInstrumentResponse from(SecurityInstrument instrument) {
        return new SecurityInstrumentResponse(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getExchange(),
                instrument.getInstrumentName(),
                instrument.getAssetType(),
                instrument.getCurrency(),
                instrument.getLotSize(),
                instrument.getStatus(),
                instrument.getCreatedAt(),
                instrument.getUpdatedAt()
        );
    }
}
