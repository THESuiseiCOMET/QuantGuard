package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.CreatePortfolioRiskConfigRequest;
import com.chenbo.quantguard.dto.PortfolioRiskConfigResponse;
import com.chenbo.quantguard.dto.UpdatePortfolioRiskConfigRequest;

public interface PortfolioRiskConfigService {

    PortfolioRiskConfigResponse create(CreatePortfolioRiskConfigRequest request);

    PortfolioRiskConfigResponse update(Long id, UpdatePortfolioRiskConfigRequest request);

    PortfolioRiskConfigResponse getById(Long id);

    PortfolioRiskConfigResponse getByPortfolioId(Long portfolioId);
}
