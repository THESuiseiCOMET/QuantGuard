package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.entity.PortfolioRiskConfig;
import com.vanyiung.quantguard.enums.RiskConfigStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioRiskConfigResponse(
        Long id,
        Long portfolioId,
        BigDecimal maxSingleOrderAmount,
        BigDecimal maxPositionRatio,
        Boolean allowBuy,
        Boolean allowSell,
        RiskConfigStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PortfolioRiskConfigResponse from(PortfolioRiskConfig config) {
        return new PortfolioRiskConfigResponse(
                config.getId(),
                config.getPortfolioId(),
                config.getMaxSingleOrderAmount(),
                config.getMaxPositionRatio(),
                config.getAllowBuy(),
                config.getAllowSell(),
                config.getStatus(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
