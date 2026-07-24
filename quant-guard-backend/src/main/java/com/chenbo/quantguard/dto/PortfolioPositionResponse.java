package com.chenbo.quantguard.dto;

import com.chenbo.quantguard.entity.PortfolioPosition;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioPositionResponse(
        Long id,
        Long portfolioId,
        Long instrumentId,
        BigDecimal totalQuantity,
        BigDecimal availableQuantity,
        BigDecimal frozenQuantity,
        BigDecimal averageCost,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PortfolioPositionResponse from(PortfolioPosition position) {
        return new PortfolioPositionResponse(
                position.getId(),
                position.getPortfolioId(),
                position.getInstrumentId(),
                position.getTotalQuantity(),
                position.getAvailableQuantity(),
                position.getFrozenQuantity(),
                position.getAverageCost(),
                position.getCreatedAt(),
                position.getUpdatedAt()
        );
    }
}
