package com.vanyiung.quantguard.controller;

import com.vanyiung.quantguard.common.ApiResponse;
import com.vanyiung.quantguard.dto.CreateSimulatedOrderRequest;
import com.vanyiung.quantguard.dto.SimulatedOrderResponse;
import com.vanyiung.quantguard.service.SimulatedOrderService;
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
public class SimulatedOrderController {

    private final SimulatedOrderService simulatedOrderService;

    public SimulatedOrderController(SimulatedOrderService simulatedOrderService) {
        this.simulatedOrderService = simulatedOrderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<SimulatedOrderResponse>> create(@Valid @RequestBody CreateSimulatedOrderRequest request) {
        SimulatedOrderResponse response = simulatedOrderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/orders")
    public ApiResponse<List<SimulatedOrderResponse>> listAll() {
        return ApiResponse.success(simulatedOrderService.listAll());
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<SimulatedOrderResponse> getById(@PathVariable @Positive(message = "id must be positive") Long id) {
        return ApiResponse.success(simulatedOrderService.getById(id));
    }

    @GetMapping("/portfolios/{portfolioId}/orders")
    public ApiResponse<List<SimulatedOrderResponse>> listByPortfolioId(
            @PathVariable @Positive(message = "portfolioId must be positive") Long portfolioId) {
        return ApiResponse.success(simulatedOrderService.listByPortfolioId(portfolioId));
    }
}
