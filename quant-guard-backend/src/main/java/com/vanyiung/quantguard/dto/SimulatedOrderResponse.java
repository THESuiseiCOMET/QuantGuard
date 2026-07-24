package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.entity.SimulatedOrder;
import com.vanyiung.quantguard.enums.OrderSide;
import com.vanyiung.quantguard.enums.OrderStatus;
import com.vanyiung.quantguard.enums.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SimulatedOrderResponse(
        Long id,
        String orderNo,
        Long accountId,
        Long portfolioId,
        Long instrumentId,
        OrderSide side,
        OrderType orderType,
        BigDecimal requestedQuantity,
        BigDecimal limitPrice,
        BigDecimal frozenAmount,
        BigDecimal frozenQuantity,
        OrderStatus status,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SimulatedOrderResponse from(SimulatedOrder order) {
        return new SimulatedOrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getAccountId(),
                order.getPortfolioId(),
                order.getInstrumentId(),
                order.getSide(),
                order.getOrderType(),
                order.getRequestedQuantity(),
                order.getLimitPrice(),
                order.getFrozenAmount(),
                order.getFrozenQuantity(),
                order.getStatus(),
                order.getRejectionReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
