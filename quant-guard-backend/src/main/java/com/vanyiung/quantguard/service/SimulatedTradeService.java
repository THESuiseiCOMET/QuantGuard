package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.SimulatedTradeResponse;

import java.util.List;

public interface SimulatedTradeService {

    List<SimulatedTradeResponse> listAll();

    SimulatedTradeResponse getById(Long id);

    List<SimulatedTradeResponse> listByOrderId(Long orderId);

    List<SimulatedTradeResponse> listByPortfolioId(Long portfolioId);
}
