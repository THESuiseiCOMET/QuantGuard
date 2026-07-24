package com.chenbo.quantguard.dto;

public record ExecuteSimulatedOrderResponse(
        SimulatedOrderResponse order,
        SimulatedTradeResponse trade
) {
}
