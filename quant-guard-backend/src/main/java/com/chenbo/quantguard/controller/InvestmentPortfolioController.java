package com.chenbo.quantguard.controller;

import com.chenbo.quantguard.common.ApiResponse;
import com.chenbo.quantguard.dto.CreateInvestmentPortfolioRequest;
import com.chenbo.quantguard.dto.InvestmentPortfolioResponse;
import com.chenbo.quantguard.service.InvestmentPortfolioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class InvestmentPortfolioController {

    private final InvestmentPortfolioService investmentPortfolioService;

    public InvestmentPortfolioController(InvestmentPortfolioService investmentPortfolioService) {
        this.investmentPortfolioService = investmentPortfolioService;
    }

    @PostMapping("/portfolios")
    public ResponseEntity<ApiResponse<InvestmentPortfolioResponse>> create(@Valid @RequestBody CreateInvestmentPortfolioRequest request) {
        InvestmentPortfolioResponse response = investmentPortfolioService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/portfolios")
    public ApiResponse<List<InvestmentPortfolioResponse>> listAll() {
        return ApiResponse.success(investmentPortfolioService.listAll());
    }

    @GetMapping("/portfolios/{id}")
    public ApiResponse<InvestmentPortfolioResponse> getById(@PathVariable @Positive(message = "id must be positive") Long id) {
        return ApiResponse.success(investmentPortfolioService.getById(id));
    }

    @GetMapping("/accounts/{accountId}/portfolios")
    public ApiResponse<List<InvestmentPortfolioResponse>> listByAccountId(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId) {
        return ApiResponse.success(investmentPortfolioService.listByAccountId(accountId));
    }
}
