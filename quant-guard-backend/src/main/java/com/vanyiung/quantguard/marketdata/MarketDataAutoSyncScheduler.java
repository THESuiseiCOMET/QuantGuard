package com.vanyiung.quantguard.marketdata;

import com.vanyiung.quantguard.dto.BatchDailyPriceSyncRequest;
import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import com.vanyiung.quantguard.service.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "quantguard.market-data", name = "auto-sync-enabled", havingValue = "true")
public class MarketDataAutoSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataAutoSyncScheduler.class);

    private final MarketDataService marketDataService;
    private final MarketDataProperties properties;

    public MarketDataAutoSyncScheduler(MarketDataService marketDataService, MarketDataProperties properties) {
        this.marketDataService = marketDataService;
        this.properties = properties;
    }

    @Scheduled(cron = "${quantguard.market-data.auto-sync-cron:0 0 18 * * MON-FRI}", zone = "Asia/Shanghai")
    public void syncImportedDailyPrices() {
        if (properties.getToken() == null || properties.getToken().isBlank()) {
            log.warn("Market data auto sync skipped because TUSHARE_TOKEN is not configured");
            return;
        }
        marketDataService.syncDailyPrices(new BatchDailyPriceSyncRequest(null, true), "SCHEDULED");
    }
}
