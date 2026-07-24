package com.vanyiung.quantguard.controller;

import com.vanyiung.quantguard.common.ApiResponse;
import com.vanyiung.quantguard.dto.BatchDailyPriceSyncRequest;
import com.vanyiung.quantguard.dto.BatchDailyPriceSyncResult;
import com.vanyiung.quantguard.service.MarketDataService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-data")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @PostMapping("/daily-prices/sync")
    public ApiResponse<BatchDailyPriceSyncResult> syncDailyPrices(
            @Valid @RequestBody BatchDailyPriceSyncRequest request) {
        return ApiResponse.success(marketDataService.syncDailyPrices(request, "BATCH"));
    }
}
