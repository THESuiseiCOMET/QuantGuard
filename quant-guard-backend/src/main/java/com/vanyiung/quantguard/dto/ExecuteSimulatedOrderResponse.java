package com.vanyiung.quantguard.dto;

public record ExecuteSimulatedOrderResponse(
        SimulatedOrderResponse order,
        SimulatedTradeResponse trade
) {
}
