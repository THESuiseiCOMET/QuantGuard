package com.chenbo.quantguard.dto;

import com.chenbo.quantguard.entity.SecurityInstrument;
import com.chenbo.quantguard.enums.AssetType;
import com.chenbo.quantguard.enums.Exchange;
import com.chenbo.quantguard.enums.InstrumentStatus;

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
