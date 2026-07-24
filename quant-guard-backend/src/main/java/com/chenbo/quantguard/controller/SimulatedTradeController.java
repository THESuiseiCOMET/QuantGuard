package com.chenbo.quantguard.controller;

import com.chenbo.quantguard.common.ApiResponse;
import com.chenbo.quantguard.dto.CashLedgerResponse;
import com.chenbo.quantguard.dto.ExecuteSimulatedOrderResponse;
import com.chenbo.quantguard.dto.SimulatedOrderResponse;
import com.chenbo.quantguard.dto.SimulatedTradeResponse;
import com.chenbo.quantguard.exception.BusinessRuleException;
import com.chenbo.quantguard.service.CashLedgerService;
import com.chenbo.quantguard.service.SimulatedOrderExecutionService;
import com.chenbo.quantguard.service.SimulatedTradeService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class SimulatedTradeController {

    private final SimulatedOrderExecutionService simulatedOrderExecutionService;
    private final SimulatedTradeService simulatedTradeService;
    private final CashLedgerService cashLedgerService;

    public SimulatedTradeController(SimulatedOrderExecutionService simulatedOrderExecutionService,
                                    SimulatedTradeService simulatedTradeService,
                                    CashLedgerService cashLedgerService) {
        this.simulatedOrderExecutionService = simulatedOrderExecutionService;
        this.simulatedTradeService = simulatedTradeService;
        this.cashLedgerService = cashLedgerService;
    }

    @PostMapping("/orders/{id}/execute")
    public ApiResponse<ExecuteSimulatedOrderResponse> execute(@PathVariable @Positive(message = "id must be positive") Long id) {
        requirePositive(id, "id");
        return ApiResponse.success(simulatedOrderExecutionService.execute(id));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<SimulatedOrderResponse> cancel(@PathVariable @Positive(message = "id must be positive") Long id) {
        requirePositive(id, "id");
        return ApiResponse.success(simulatedOrderExecutionService.cancel(id));
    }

    @GetMapping("/trades")
    public ApiResponse<List<SimulatedTradeResponse>> listTrades() {
        return ApiResponse.success(simulatedTradeService.listAll());
    }

    @GetMapping("/trades/{id}")
    public ApiResponse<SimulatedTradeResponse> getTradeById(@PathVariable @Positive(message = "id must be positive") Long id) {
        requirePositive(id, "id");
        return ApiResponse.success(simulatedTradeService.getById(id));
    }

    @GetMapping("/orders/{orderId}/trades")
    public ApiResponse<List<SimulatedTradeResponse>> listTradesByOrderId(
            @PathVariable @Positive(message = "orderId must be positive") Long orderId) {
        requirePositive(orderId, "orderId");
        return ApiResponse.success(simulatedTradeService.listByOrderId(orderId));
    }

    @GetMapping("/portfolios/{portfolioId}/trades")
    public ApiResponse<List<SimulatedTradeResponse>> listTradesByPortfolioId(
            @PathVariable @Positive(message = "portfolioId must be positive") Long portfolioId) {
        requirePositive(portfolioId, "portfolioId");
        return ApiResponse.success(simulatedTradeService.listByPortfolioId(portfolioId));
    }

    @GetMapping("/accounts/{accountId}/cash-ledger")
    public ApiResponse<List<CashLedgerResponse>> listCashLedgerByAccountId(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId) {
        requirePositive(accountId, "accountId");
        return ApiResponse.success(cashLedgerService.listByAccountId(accountId));
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new BusinessRuleException(name + " must be positive");
        }
    }
}
