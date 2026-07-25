package com.vanyiung.quantguard.dto;

public record MarketDataProviderConfigResponse(
        String provider,
        boolean tokenConfigured
) {
}
