package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.PortfolioPositionResponse;
import com.vanyiung.quantguard.dto.UpdatePortfolioPositionRequest;

import java.util.List;

public interface PortfolioPositionService {

    List<PortfolioPositionResponse> listByPortfolioId(Long portfolioId);

    PortfolioPositionResponse getById(Long id);

    PortfolioPositionResponse update(Long id, UpdatePortfolioPositionRequest request);

    void delete(Long id);
}
