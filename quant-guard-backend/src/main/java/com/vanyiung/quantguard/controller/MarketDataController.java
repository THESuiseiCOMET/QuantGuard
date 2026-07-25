package com.vanyiung.quantguard.controller;

import com.vanyiung.quantguard.common.ApiResponse;
import com.vanyiung.quantguard.dto.BatchDailyPriceSyncRequest;
import com.vanyiung.quantguard.dto.BatchDailyPriceSyncResult;
import com.vanyiung.quantguard.dto.MarketDataProviderConfigResponse;
import com.vanyiung.quantguard.dto.UpdateMarketDataProviderConfigRequest;
import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import com.vanyiung.quantguard.service.MarketDataService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-data")
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final MarketDataProperties marketDataProperties;

    public MarketDataController(MarketDataService marketDataService,
                                MarketDataProperties marketDataProperties) {
        this.marketDataService = marketDataService;
        this.marketDataProperties = marketDataProperties;
    }

    @GetMapping({"/provider-config", "/config"})
    public ApiResponse<MarketDataProviderConfigResponse> getProviderConfig() {
        return ApiResponse.success(new MarketDataProviderConfigResponse(
                marketDataProperties.getProvider(),
                marketDataProperties.getToken() != null && !marketDataProperties.getToken().isBlank()
        ));
    }

    @PutMapping({"/provider-config", "/config"})
    public ApiResponse<MarketDataProviderConfigResponse> updateProviderConfig(
            @Valid @RequestBody UpdateMarketDataProviderConfigRequest request) {
        marketDataProperties.setToken(request.token().trim());
        return getProviderConfig();
    }

    @PostMapping("/daily-prices/sync")
    public ApiResponse<BatchDailyPriceSyncResult> syncDailyPrices(
            @Valid @RequestBody BatchDailyPriceSyncRequest request) {
        return ApiResponse.success(marketDataService.syncDailyPrices(request, "BATCH"));
    }
}
