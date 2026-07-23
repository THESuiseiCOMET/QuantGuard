package com.chenbo.quantguard.controller;

import com.chenbo.quantguard.common.ApiResponse;
import com.chenbo.quantguard.dto.CreateSimulationAccountRequest;
import com.chenbo.quantguard.dto.SimulationAccountResponse;
import com.chenbo.quantguard.service.SimulationAccountService;
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
@RequestMapping("/api/accounts")
public class SimulationAccountController {

    private final SimulationAccountService simulationAccountService;

    public SimulationAccountController(SimulationAccountService simulationAccountService) {
        this.simulationAccountService = simulationAccountService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SimulationAccountResponse>> create(@Valid @RequestBody CreateSimulationAccountRequest request) {
        SimulationAccountResponse response = simulationAccountService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<SimulationAccountResponse>> listAll() {
        return ApiResponse.success(simulationAccountService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SimulationAccountResponse> getById(@PathVariable @Positive(message = "id must be positive") Long id) {
        return ApiResponse.success(simulationAccountService.getById(id));
    }
}
