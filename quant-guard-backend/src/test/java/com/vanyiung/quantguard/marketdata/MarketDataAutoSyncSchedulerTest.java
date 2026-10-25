package com.vanyiung.quantguard.marketdata;

import com.vanyiung.quantguard.dto.BatchDailyPriceSyncRequest;
import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import com.vanyiung.quantguard.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MarketDataAutoSyncSchedulerTest {

    private final MarketDataService service = mock(MarketDataService.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(MarketDataService.class, () -> service)
            .withUserConfiguration(MarketDataAutoSyncScheduler.class);

    @Test
    void autoSyncIsDisabledByDefault() {
        assertThat(new MarketDataProperties().isAutoSyncEnabled()).isFalse();
        runner.run(context -> assertThat(context).doesNotHaveBean(MarketDataAutoSyncScheduler.class));
        verifyNoInteractions(service);
    }

    @Test
    void explicitlyDisabledAutoSyncDoesNotCreateScheduler() {
        runner.withPropertyValues("quantguard.market-data.auto-sync-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MarketDataAutoSyncScheduler.class));
        verifyNoInteractions(service);
    }

    @Test
    void explicitlyEnabledAutoSyncRunsWithoutCredentials() {
        runner.withPropertyValues("quantguard.market-data.auto-sync-enabled=true").run(context -> {
            assertThat(context).hasSingleBean(MarketDataAutoSyncScheduler.class);
            context.getBean(MarketDataAutoSyncScheduler.class).syncImportedDailyPrices();
            verify(service).syncDailyPrices(new BatchDailyPriceSyncRequest(null, true), "SCHEDULED");
        });
    }
}
