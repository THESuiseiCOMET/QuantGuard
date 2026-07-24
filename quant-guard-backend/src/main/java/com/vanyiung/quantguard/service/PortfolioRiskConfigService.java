package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreatePortfolioRiskConfigRequest;
import com.vanyiung.quantguard.dto.PortfolioRiskConfigResponse;
import com.vanyiung.quantguard.dto.UpdatePortfolioRiskConfigRequest;

public interface PortfolioRiskConfigService {

    PortfolioRiskConfigResponse create(CreatePortfolioRiskConfigRequest request);

    PortfolioRiskConfigResponse update(Long id, UpdatePortfolioRiskConfigRequest request);

    PortfolioRiskConfigResponse getById(Long id);

    PortfolioRiskConfigResponse getByPortfolioId(Long portfolioId);
}
