package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.PortfolioPositionResponse;

import java.util.List;

public interface PortfolioPositionService {

    List<PortfolioPositionResponse> listByPortfolioId(Long portfolioId);

    PortfolioPositionResponse getById(Long id);
}
