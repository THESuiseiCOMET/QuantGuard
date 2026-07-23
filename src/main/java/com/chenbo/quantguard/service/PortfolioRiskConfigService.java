package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.CreatePortfolioRiskConfigRequest;
import com.chenbo.quantguard.dto.PortfolioRiskConfigResponse;

public interface PortfolioRiskConfigService {

    PortfolioRiskConfigResponse create(CreatePortfolioRiskConfigRequest request);

    PortfolioRiskConfigResponse getById(Long id);

    PortfolioRiskConfigResponse getByPortfolioId(Long portfolioId);
}
