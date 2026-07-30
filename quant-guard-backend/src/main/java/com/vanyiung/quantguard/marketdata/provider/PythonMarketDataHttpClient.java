package com.vanyiung.quantguard.marketdata.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class PythonMarketDataHttpClient {

    private final MarketDataProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PythonMarketDataHttpClient(MarketDataProperties properties) {
        this(properties, new ObjectMapper(), HttpClient.newBuilder()
                .connectTimeout(properties.getPython().getConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build());
    }

    PythonMarketDataHttpClient(MarketDataProperties properties,
                               ObjectMapper objectMapper,
                               HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public JsonNode post(String path, Object requestBody) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder(resolve(path))
                    .timeout(properties.getPython().getReadTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw httpError(response);
            }
            return parse(response.body());
        } catch (MarketDataProviderException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.TIMEOUT,
                    "Python market data service request timed out", ex);
        } catch (IOException ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.UPSTREAM_UNAVAILABLE,
                    "Python market data service is unavailable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MarketDataProviderException(MarketDataProviderErrorType.UPSTREAM_UNAVAILABLE,
                    "Python market data service request was interrupted", ex);
        }
    }

    JsonNode parse(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.UPSTREAM_INVALID_RESPONSE,
                    "Python market data service returned invalid JSON", ex);
        }
    }

    private URI resolve(String path) {
        String baseUrl = properties.getPython().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.NOT_CONFIGURED,
                    "Python market data service base URL is not configured");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        try {
            return URI.create(normalizedBase).resolve(normalizedPath);
        } catch (IllegalArgumentException ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.NOT_CONFIGURED,
                    "Python market data service base URL is invalid", ex);
        }
    }

    private MarketDataProviderException httpError(HttpResponse<String> response) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            String code = root.path("code").asText("");
            String message = root.path("message").asText("");
            return new MarketDataProviderException(errorType(code), message.isBlank()
                    ? "Python market data service returned HTTP " + response.statusCode()
                    : message);
        } catch (Exception ignored) {
            return new MarketDataProviderException(MarketDataProviderErrorType.UPSTREAM_UNAVAILABLE,
                    "Python market data service returned HTTP " + response.statusCode());
        }
    }

    static MarketDataProviderErrorType errorType(String code) {
        if (code == null || code.isBlank()) {
            return MarketDataProviderErrorType.UPSTREAM_INVALID_RESPONSE;
        }
        try {
            return MarketDataProviderErrorType.valueOf(code);
        } catch (IllegalArgumentException ex) {
            return MarketDataProviderErrorType.UPSTREAM_INVALID_RESPONSE;
        }
    }
}
