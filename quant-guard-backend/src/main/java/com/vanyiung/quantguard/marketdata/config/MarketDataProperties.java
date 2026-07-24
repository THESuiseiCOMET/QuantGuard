package com.vanyiung.quantguard.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@ConfigurationProperties(prefix = "quantguard.market-data")
public class MarketDataProperties {

    private String provider = "tushare";

    private String baseUrl = "https://api.tushare.pro";

    private String token = "";

    private LocalDate defaultStartDate = LocalDate.of(2010, 1, 1);

    private boolean autoSyncEnabled = false;

    private String autoSyncCron = "0 0 18 * * MON-FRI";

    private int batchSize = 20;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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
}
