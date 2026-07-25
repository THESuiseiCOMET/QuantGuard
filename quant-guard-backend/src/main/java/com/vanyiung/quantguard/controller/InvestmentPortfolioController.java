package com.vanyiung.quantguard.controller;

import com.vanyiung.quantguard.common.ApiResponse;
import com.vanyiung.quantguard.dto.CreateInvestmentPortfolioRequest;
import com.vanyiung.quantguard.dto.InvestmentPortfolioResponse;
import com.vanyiung.quantguard.dto.UpdateAccountStatusRequest;
import com.vanyiung.quantguard.service.InvestmentPortfolioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @PatchMapping("/portfolios/{id}/status")
    public ApiResponse<InvestmentPortfolioResponse> updateStatus(
            @PathVariable @Positive(message = "id must be positive") Long id,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ApiResponse.success(investmentPortfolioService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/portfolios/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable @Positive(message = "id must be positive") Long id) {
        investmentPortfolioService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/portfolios/{id}/force")
    public ResponseEntity<ApiResponse<Void>> forceDelete(@PathVariable @Positive(message = "id must be positive") Long id) {
        investmentPortfolioService.forceDelete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
