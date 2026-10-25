package com.vanyiung.quantguard.marketdata;

import com.vanyiung.quantguard.dto.BatchDailyPriceSyncRequest;
import com.vanyiung.quantguard.service.MarketDataService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "quantguard.market-data", name = "auto-sync-enabled", havingValue = "true")
public class MarketDataAutoSyncScheduler {

    private final MarketDataService marketDataService;

    public MarketDataAutoSyncScheduler(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Scheduled(cron = "${quantguard.market-data.auto-sync-cron:0 0 18 * * MON-FRI}", zone = "Asia/Shanghai")
    public void syncImportedDailyPrices() {
        marketDataService.syncDailyPrices(new BatchDailyPriceSyncRequest(null, true), "SCHEDULED");
    }
}
