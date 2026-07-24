package com.chenbo.quantguard.controller;

import com.chenbo.quantguard.common.ApiResponse;
import com.chenbo.quantguard.dto.CreatePortfolioRiskConfigRequest;
import com.chenbo.quantguard.dto.PortfolioRiskConfigResponse;
import com.chenbo.quantguard.dto.UpdatePortfolioRiskConfigRequest;
import com.chenbo.quantguard.service.PortfolioRiskConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class PortfolioRiskConfigController {

    private final PortfolioRiskConfigService portfolioRiskConfigService;

    public PortfolioRiskConfigController(PortfolioRiskConfigService portfolioRiskConfigService) {
        this.portfolioRiskConfigService = portfolioRiskConfigService;
    }

    @PostMapping("/risk-configs")
    public ResponseEntity<ApiResponse<PortfolioRiskConfigResponse>> create(@Valid @RequestBody CreatePortfolioRiskConfigRequest request) {
        PortfolioRiskConfigResponse response = portfolioRiskConfigService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/risk-configs/{id}")
    public ApiResponse<PortfolioRiskConfigResponse> update(@PathVariable @Positive(message = "id must be positive") Long id,
                                                           @Valid @RequestBody UpdatePortfolioRiskConfigRequest request) {
        return ApiResponse.success(portfolioRiskConfigService.update(id, request));
    }

    @GetMapping("/risk-configs/{id}")
    public ApiResponse<PortfolioRiskConfigResponse> getById(@PathVariable @Positive(message = "id must be positive") Long id) {
        return ApiResponse.success(portfolioRiskConfigService.getById(id));
    }

    @GetMapping("/portfolios/{portfolioId}/risk-config")
    public ApiResponse<PortfolioRiskConfigResponse> getByPortfolioId(
            @PathVariable @Positive(message = "portfolioId must be positive") Long portfolioId) {
        return ApiResponse.success(portfolioRiskConfigService.getByPortfolioId(portfolioId));
    }
}
