package com.vanyiung.quantguard.marketdata.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PythonMarketDataHttpClientTest {

    @Test
    void mapsUnavailableServiceWithoutConnectingDuringConstruction() throws Exception {
        MarketDataProperties properties = new MarketDataProperties();
        HttpClient transport = mock(HttpClient.class);
        when(transport.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection refused"));
        PythonMarketDataHttpClient client = new PythonMarketDataHttpClient(
                properties,
                new ObjectMapper(),
                transport);

        MarketDataProviderException error = assertThrows(
                MarketDataProviderException.class,
                () -> client.post("/v1/instruments/lookup", Map.of("symbols", java.util.List.of("600519.SH"))));

        assertEquals(MarketDataProviderErrorType.UPSTREAM_UNAVAILABLE, error.errorType());
    }

    @Test
    void rejectsInvalidJson() {
        PythonMarketDataHttpClient client = new PythonMarketDataHttpClient(
                new MarketDataProperties(),
                new ObjectMapper(),
                mock(HttpClient.class));

        MarketDataProviderException error = assertThrows(
                MarketDataProviderException.class,
                () -> client.parse("<html>not-json</html>"));

        assertEquals(MarketDataProviderErrorType.UPSTREAM_INVALID_RESPONSE, error.errorType());
    }
}
