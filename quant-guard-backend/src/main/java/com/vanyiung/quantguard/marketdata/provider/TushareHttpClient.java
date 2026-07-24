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
import java.time.Duration;
import java.util.Map;

@Component
public class TushareHttpClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final MarketDataProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public TushareHttpClient(MarketDataProperties properties) {
        this(properties, new ObjectMapper());
    }

    TushareHttpClient(MarketDataProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    public TushareResponse post(String apiName, Map<String, Object> params, String fields) {
        if (properties.getToken() == null || properties.getToken().isBlank()) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.NOT_CONFIGURED,
                    "Market data provider is not configured. Please set TUSHARE_TOKEN.");
        }

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "api_name", apiName,
                    "token", properties.getToken(),
                    "params", params,
                    "fields", fields
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new MarketDataProviderException(MarketDataProviderErrorType.RATE_LIMITED,
                        "Market data provider rate limit exceeded");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MarketDataProviderException(MarketDataProviderErrorType.NETWORK_ERROR,
                        "Market data provider returned HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.TIMEOUT,
                    "Market data provider request timed out", ex);
        } catch (IOException ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.NETWORK_ERROR,
                    "Market data provider network error", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MarketDataProviderException(MarketDataProviderErrorType.NETWORK_ERROR,
                    "Market data provider request interrupted", ex);
        }
    }

    TushareResponse parse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.has("code") || !root.has("msg")) {
                throw new MarketDataProviderException(MarketDataProviderErrorType.INVALID_RESPONSE,
                        "Market data provider response is missing code or msg");
            }
            int code = root.get("code").asInt();
            String message = root.get("msg").asText("");
            if (code != 0) {
                MarketDataProviderErrorType type = classifyProviderError(message);
                throw new MarketDataProviderException(type, message.isBlank()
                        ? "Market data provider returned error code " + code
                        : message);
            }
            JsonNode data = root.get("data");
            if (data == null || !data.has("fields") || !data.has("items")) {
                throw new MarketDataProviderException(MarketDataProviderErrorType.INVALID_RESPONSE,
                        "Market data provider response is missing data.fields or data.items");
            }
            if (!data.get("fields").isArray() || !data.get("items").isArray()) {
                throw new MarketDataProviderException(MarketDataProviderErrorType.INVALID_RESPONSE,
                        "Market data provider fields and items must be arrays");
            }
            return new TushareResponse(data.get("fields"), data.get("items"));
        } catch (MarketDataProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.INVALID_RESPONSE,
                    "Market data provider response cannot be parsed", ex);
        }
    }

    private MarketDataProviderErrorType classifyProviderError(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("permission") || lower.contains("权限") || lower.contains("积分")) {
            return MarketDataProviderErrorType.PERMISSION_DENIED;
        }
        if (lower.contains("limit") || lower.contains("频次") || lower.contains("rate")) {
            return MarketDataProviderErrorType.RATE_LIMITED;
        }
        return MarketDataProviderErrorType.INVALID_RESPONSE;
    }
}
