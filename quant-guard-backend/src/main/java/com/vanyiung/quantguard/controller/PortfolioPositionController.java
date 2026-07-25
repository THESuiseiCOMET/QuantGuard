package com.vanyiung.quantguard.controller;

import com.vanyiung.quantguard.common.ApiResponse;
import com.vanyiung.quantguard.dto.PortfolioPositionResponse;
import com.vanyiung.quantguard.dto.UpdatePortfolioPositionRequest;
import com.vanyiung.quantguard.service.PortfolioPositionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PutMapping("/positions/{id}")
    public ApiResponse<PortfolioPositionResponse> update(@PathVariable @Positive(message = "id must be positive") Long id,
                                                         @Valid @RequestBody UpdatePortfolioPositionRequest request) {
        return ApiResponse.success(portfolioPositionService.update(id, request));
    }

    @DeleteMapping("/positions/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "id must be positive") Long id) {
        portfolioPositionService.delete(id);
        return ApiResponse.success(null);
    }
}
