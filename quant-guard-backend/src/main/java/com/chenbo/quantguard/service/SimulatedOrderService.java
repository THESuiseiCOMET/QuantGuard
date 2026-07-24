package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.CreateSimulatedOrderRequest;
import com.chenbo.quantguard.dto.SimulatedOrderResponse;

import java.util.List;

public interface SimulatedOrderService {

    SimulatedOrderResponse create(CreateSimulatedOrderRequest request);

    List<SimulatedOrderResponse> listAll();

    SimulatedOrderResponse getById(Long id);

    List<SimulatedOrderResponse> listByPortfolioId(Long portfolioId);
}
