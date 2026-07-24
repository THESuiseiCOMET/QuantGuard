package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateSimulatedOrderRequest;
import com.vanyiung.quantguard.dto.SimulatedOrderResponse;

import java.util.List;

public interface SimulatedOrderService {

    SimulatedOrderResponse create(CreateSimulatedOrderRequest request);

    List<SimulatedOrderResponse> listAll();

    SimulatedOrderResponse getById(Long id);

    List<SimulatedOrderResponse> listByPortfolioId(Long portfolioId);
}
