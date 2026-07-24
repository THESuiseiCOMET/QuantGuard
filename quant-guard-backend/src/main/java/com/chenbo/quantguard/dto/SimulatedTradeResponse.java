package com.chenbo.quantguard.dto;

import com.chenbo.quantguard.entity.SimulatedTrade;
import com.chenbo.quantguard.enums.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SimulatedTradeResponse(
        Long id,
        String tradeNo,
        Long orderId,
        Long accountId,
        Long portfolioId,
        Long instrumentId,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal executionPrice,
        BigDecimal tradeAmount,
        LocalDateTime executedAt,
        LocalDateTime createdAt
) {

    public static SimulatedTradeResponse from(SimulatedTrade trade) {
        return new SimulatedTradeResponse(
                trade.getId(),
                trade.getTradeNo(),
                trade.getOrderId(),
                trade.getAccountId(),
                trade.getPortfolioId(),
                trade.getInstrumentId(),
                trade.getSide(),
                trade.getQuantity(),
                trade.getExecutionPrice(),
                trade.getTradeAmount(),
                trade.getExecutedAt(),
                trade.getCreatedAt()
        );
    }
}
