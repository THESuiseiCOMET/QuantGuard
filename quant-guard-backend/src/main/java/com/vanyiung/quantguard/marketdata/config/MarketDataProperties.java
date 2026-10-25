package com.vanyiung.quantguard.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.LocalDate;

@ConfigurationProperties(prefix = "quantguard.market-data")
public class MarketDataProperties {

    private String provider = "python";

    private LocalDate defaultStartDate = LocalDate.of(1990, 1, 1);

    private boolean autoSyncEnabled = false;

    private String autoSyncCron = "0 0 18 * * MON-FRI";

    private int batchSize = 20;

    private Python python = new Python();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public LocalDate getDefaultStartDate() {
        return defaultStartDate;
    }

    public void setDefaultStartDate(LocalDate defaultStartDate) {
        this.defaultStartDate = defaultStartDate;
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

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
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

        private Duration readTimeout = Duration.ofSeconds(30);

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
