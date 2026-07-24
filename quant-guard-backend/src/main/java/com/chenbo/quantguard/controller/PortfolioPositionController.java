package com.chenbo.quantguard.controller;

import com.chenbo.quantguard.common.ApiResponse;
import com.chenbo.quantguard.dto.PortfolioPositionResponse;
import com.chenbo.quantguard.service.PortfolioPositionService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class PortfolioPositionController {

    private final PortfolioPositionService portfolioPositionService;

    public PortfolioPositionController(PortfolioPositionService portfolioPositionService) {
        this.portfolioPositionService = portfolioPositionService;
    }

    @GetMapping("/portfolios/{portfolioId}/positions")
    public ApiResponse<List<PortfolioPositionResponse>> listByPortfolioId(
            @PathVariable @Positive(message = "portfolioId must be positive") Long portfolioId) {
        return ApiResponse.success(portfolioPositionService.listByPortfolioId(portfolioId));
    }

    @GetMapping("/positions/{id}")
    public ApiResponse<PortfolioPositionResponse> getById(@PathVariable @Positive(message = "id must be positive") Long id) {
        return ApiResponse.success(portfolioPositionService.getById(id));
    }
}
