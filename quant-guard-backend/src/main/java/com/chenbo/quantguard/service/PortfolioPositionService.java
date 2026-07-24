package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.PortfolioPositionResponse;

import java.util.List;

public interface PortfolioPositionService {

    List<PortfolioPositionResponse> listByPortfolioId(Long portfolioId);

    PortfolioPositionResponse getById(Long id);
}
