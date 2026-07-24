package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.ExecuteSimulatedOrderResponse;
import com.vanyiung.quantguard.dto.SimulatedOrderResponse;

public interface SimulatedOrderExecutionService {

    ExecuteSimulatedOrderResponse execute(Long orderId);

    SimulatedOrderResponse cancel(Long orderId);
}
