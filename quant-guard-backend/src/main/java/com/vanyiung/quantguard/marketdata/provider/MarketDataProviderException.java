package com.vanyiung.quantguard.marketdata.provider;

public class MarketDataProviderException extends RuntimeException {

    private final MarketDataProviderErrorType errorType;

    public MarketDataProviderException(MarketDataProviderErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public MarketDataProviderException(MarketDataProviderErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public MarketDataProviderErrorType errorType() {
        return errorType;
    }
}
