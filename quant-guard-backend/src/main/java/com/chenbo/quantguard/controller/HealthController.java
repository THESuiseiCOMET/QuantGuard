package com.chenbo.quantguard.controller;

import com.chenbo.quantguard.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        Map<String, String> healthData = Map.of(
                "application", "quant-guard-backend",
                "status", "running"
        );

        return ApiResponse.success(healthData);
    }
}
