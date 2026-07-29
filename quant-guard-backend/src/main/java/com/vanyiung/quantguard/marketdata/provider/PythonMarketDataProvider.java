package com.vanyiung.quantguard.marketdata.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PythonMarketDataProvider implements MarketDataProvider {

    private final PythonMarketDataHttpClient httpClient;

    public PythonMarketDataProvider(PythonMarketDataHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<ProviderInstrumentMetadata> searchInstruments(String query, int limit) {
        JsonNode root = httpClient.post("/v1/instruments/search", Map.of(
                "query", query,
                "limit", limit
        ));
        JsonNode results = requiredArray(root, "results");
        List<ProviderInstrumentMetadata> metadata = new ArrayList<>();
        for (JsonNode result : results) {
            metadata.add(mapInstrument(result));
        }
        return metadata;
    }

    @Override
    public List<ProviderInstrumentMetadata> lookupInstruments(List<MarketSymbol> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        JsonNode root = httpClient.post("/v1/instruments/lookup", Map.of(
                "symbols", symbols.stream().map(MarketSymbol::canonicalCode).toList()
        ));
        validateBatchStatus(root);
        JsonNode results = requiredArray(root, "results");
        List<ProviderInstrumentMetadata> metadata = new ArrayList<>();
        MarketDataProviderException firstError = null;
        for (JsonNode result : results) {
            if (hasError(result)) {
                if (firstError == null) {
                    firstError = providerError(result.get("error"));
                }
                continue;
            }
            metadata.add(mapInstrument(requiredObject(result, "instrument")));
        }
        if (metadata.isEmpty() && firstError != null) {
            throw firstError;
        }
        return metadata;
    }

    @Override
    public List<ProviderDailyBar> fetchDailyBars(List<MarketSymbol> symbols,
                                                 LocalDate startDate,
                                                 LocalDate endDate) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        JsonNode root = httpClient.post("/v1/daily-bars", Map.of(
                "symbols", symbols.stream().map(MarketSymbol::canonicalCode).toList(),
                "start_date", startDate.toString(),
                "end_date", endDate.toString()
        ));
        validateBatchStatus(root);
        JsonNode results = requiredArray(root, "results");
        List<ProviderDailyBar> bars = new ArrayList<>();
        MarketDataProviderException firstError = null;
        for (JsonNode result : results) {
            if (hasError(result)) {
                if (firstError == null) {
                    firstError = providerError(result.get("error"));
                }
                continue;
            }
            JsonNode resultBars = requiredArray(result, "bars");
            for (JsonNode bar : resultBars) {
                bars.add(mapDailyBar(bar));
            }
        }
        if (bars.isEmpty() && firstError != null) {
            throw firstError;
        }
        return bars;
    }

    @Override
    public String providerName() {
        return "PYTHON";
    }

    private ProviderInstrumentMetadata mapInstrument(JsonNode item) {
        MarketSymbol symbol = parseCanonicalCode(requiredText(item, "symbol"));
        Exchange exchange = parseExchange(requiredText(item, "exchange"));
        ProviderInstrumentType instrumentType = parseInstrumentType(requiredText(item, "instrument_type"));
        return new ProviderInstrumentMetadata(
                symbol,
                requiredText(item, "name"),
                exchange,
                requiredText(item, "currency"),
                requiredText(item, "list_status"),
                optionalDate(item.get("list_date"), "list_date"),
                instrumentType
        );
    }

    private ProviderDailyBar mapDailyBar(JsonNode item) {
        return new ProviderDailyBar(
                parseCanonicalCode(requiredText(item, "symbol")),
                requiredDate(item, "trade_date"),
                requiredDecimal(item, "open"),
                requiredDecimal(item, "high"),
                requiredDecimal(item, "low"),
                requiredDecimal(item, "close"),
                optionalDecimal(item.get("previous_close"), "previous_close"),
                optionalDecimal(item.get("change"), "change"),
                optionalDecimal(item.get("percentage_change"), "percentage_change"),
                requiredDecimal(item, "volume_lots"),
                requiredDecimal(item, "amount_cny")
        );
    }

    private boolean hasError(JsonNode result) {
        JsonNode error = result.get("error");
        return error != null && !error.isNull();
    }

    private void validateBatchStatus(JsonNode root) {
        String status = requiredText(root, "status");
        if (!List.of("SUCCESS", "PARTIAL_FAILURE", "FAILED").contains(status)) {
            throw invalidResponse("Python market data response has invalid status: " + status);
        }
    }

    private MarketDataProviderException providerError(JsonNode error) {
        String code = requiredText(error, "code");
        String message = requiredText(error, "message");
        return new MarketDataProviderException(PythonMarketDataHttpClient.errorType(code), message);
    }

    private JsonNode requiredArray(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isArray()) {
            throw invalidResponse("Python market data response field must be an array: " + field);
        }
        return value;
    }

    private JsonNode requiredObject(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isObject()) {
            throw invalidResponse("Python market data response field must be an object: " + field);
        }
        return value;
    }

    private String requiredText(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw invalidResponse("Python market data response has invalid text field: " + field);
        }
        return value.asText();
    }

    private BigDecimal requiredDecimal(JsonNode parent, String field) {
        BigDecimal value = optionalDecimal(parent == null ? null : parent.get(field), field);
        if (value == null) {
            throw invalidResponse("Python market data response has null decimal field: " + field);
        }
        return value;
    }

    private BigDecimal optionalDecimal(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalidResponse("Python market data response decimal must be a string: " + field);
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.UPSTREAM_INVALID_RESPONSE,
                    "Python market data response has invalid decimal: " + field, ex);
        }
    }

    private LocalDate requiredDate(JsonNode parent, String field) {
        LocalDate value = optionalDate(parent == null ? null : parent.get(field), field);
        if (value == null) {
            throw invalidResponse("Python market data response has null date field: " + field);
        }
        return value;
    }

    private LocalDate optionalDate(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalidResponse("Python market data response date must be a string: " + field);
        }
        try {
            return LocalDate.parse(value.asText());
        } catch (DateTimeParseException ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.UPSTREAM_INVALID_RESPONSE,
                    "Python market data response has invalid date: " + field, ex);
        }
    }

    private MarketSymbol parseCanonicalCode(String code) {
        if (code.matches("\\d{6}\\.SH")) {
            return new MarketSymbol(code.substring(0, 6), Exchange.SSE);
        }
        if (code.matches("\\d{6}\\.SZ")) {
            return new MarketSymbol(code.substring(0, 6), Exchange.SZSE);
        }
        throw invalidResponse("Python market data response has invalid symbol: " + code);
    }

    private Exchange parseExchange(String exchange) {
        return switch (exchange) {
            case "SSE" -> Exchange.SSE;
            case "SZSE" -> Exchange.SZSE;
            default -> throw invalidResponse("Python market data response has invalid exchange: " + exchange);
        };
    }

    private ProviderInstrumentType parseInstrumentType(String value) {
        try {
            return ProviderInstrumentType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.UPSTREAM_INVALID_RESPONSE,
                    "Python market data response has invalid instrument type: " + value, ex);
        }
    }

    private MarketDataProviderException invalidResponse(String message) {
        return new MarketDataProviderException(MarketDataProviderErrorType.UPSTREAM_INVALID_RESPONSE, message);
    }
}
