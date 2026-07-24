package com.vanyiung.quantguard.controller;

import com.vanyiung.quantguard.common.ApiResponse;
import com.vanyiung.quantguard.dto.CreateSecurityInstrumentRequest;
import com.vanyiung.quantguard.dto.SecurityInstrumentResponse;
import com.vanyiung.quantguard.service.SecurityInstrumentService;
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
@RequestMapping("/api/instruments")
public class SecurityInstrumentController {

    private final SecurityInstrumentService securityInstrumentService;

    public SecurityInstrumentController(SecurityInstrumentService securityInstrumentService) {
        this.securityInstrumentService = securityInstrumentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SecurityInstrumentResponse>> create(@Valid @RequestBody CreateSecurityInstrumentRequest request) {
        SecurityInstrumentResponse response = securityInstrumentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<SecurityInstrumentResponse>> listAll() {
        return ApiResponse.success(securityInstrumentService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SecurityInstrumentResponse> getById(@PathVariable @Positive(message = "id must be positive") Long id) {
        return ApiResponse.success(securityInstrumentService.getById(id));
    }
}
