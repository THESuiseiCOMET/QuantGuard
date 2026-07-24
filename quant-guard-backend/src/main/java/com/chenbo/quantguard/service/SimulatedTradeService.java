package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.SimulatedTradeResponse;

import java.util.List;

public interface SimulatedTradeService {

    List<SimulatedTradeResponse> listAll();

    SimulatedTradeResponse getById(Long id);

    List<SimulatedTradeResponse> listByOrderId(Long orderId);

    List<SimulatedTradeResponse> listByPortfolioId(Long portfolioId);
}
