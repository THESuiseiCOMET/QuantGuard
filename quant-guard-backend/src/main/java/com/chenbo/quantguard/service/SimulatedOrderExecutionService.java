package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.ExecuteSimulatedOrderResponse;
import com.chenbo.quantguard.dto.SimulatedOrderResponse;

public interface SimulatedOrderExecutionService {

    ExecuteSimulatedOrderResponse execute(Long orderId);

    SimulatedOrderResponse cancel(Long orderId);
}
