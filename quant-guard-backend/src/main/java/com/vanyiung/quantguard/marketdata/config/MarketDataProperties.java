package com.vanyiung.quantguard.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.LocalDate;

@ConfigurationProperties(prefix = "quantguard.market-data")
public class MarketDataProperties {

    private LocalDate defaultStartDate = LocalDate.of(1990, 1, 1);

    private int initialLookbackYears = 1;

    private boolean autoSyncEnabled = false;

    private String autoSyncCron = "0 0 18 * * MON-FRI";

    private Python python = new Python();

    public LocalDate getDefaultStartDate() {
        return defaultStartDate;
    }

    public void setDefaultStartDate(LocalDate defaultStartDate) {
        this.defaultStartDate = defaultStartDate;
    }

    public int getInitialLookbackYears() {
        return initialLookbackYears;
    }

    public void setInitialLookbackYears(int initialLookbackYears) {
        if (initialLookbackYears < 1 || initialLookbackYears > 30) {
            throw new IllegalArgumentException("initial-lookback-years must be between 1 and 30");
        }
        this.initialLookbackYears = initialLookbackYears;
    }

    public boolean isAutoSyncEnabled() {
        return autoSyncEnabled;
    }

    public void setAutoSyncEnabled(boolean autoSyncEnabled) {
        this.autoSyncEnabled = autoSyncEnabled;
    }

    public String getAutoSyncCron() {
        return autoSyncCron;
    }

    public void setAutoSyncCron(String autoSyncCron) {
        this.autoSyncCron = autoSyncCron;
    }

    public Python getPython() {
        return python;
    }

    public void setPython(Python python) {
        this.python = python;
    }

    public static class Python {

        private String baseUrl = "http://127.0.0.1:8001";

        private Duration connectTimeout = Duration.ofSeconds(3);

        private Duration readTimeout = Duration.ofSeconds(60);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}
