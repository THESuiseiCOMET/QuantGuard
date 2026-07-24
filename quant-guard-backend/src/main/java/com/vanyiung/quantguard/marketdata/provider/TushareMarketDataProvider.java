package com.vanyiung.quantguard.marketdata.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TushareMarketDataProvider implements MarketDataProvider {

    private static final DateTimeFormatter TUSHARE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final String STOCK_BASIC_FIELDS = "ts_code,symbol,name,exchange,curr_type,list_status,list_date";
    private static final String DAILY_FIELDS =
            "ts_code,trade_date,open,high,low,close,pre_close,change,pct_chg,vol,amount";

    private final TushareHttpClient httpClient;

    public TushareMarketDataProvider(TushareHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<ProviderInstrumentMetadata> lookupInstruments(List<MarketSymbol> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }

        List<ProviderInstrumentMetadata> result = new ArrayList<>();
        for (MarketSymbol symbol : symbols) {
            TushareResponse response = httpClient.post("stock_basic", Map.of(
                    "ts_code", symbol.tushareCode(),
                    "list_status", "L"
            ), STOCK_BASIC_FIELDS);
            Map<String, Integer> index = indexFields(response.fields(), requiredFields(STOCK_BASIC_FIELDS));
            for (JsonNode item : response.items()) {
                ProviderInstrumentMetadata metadata = mapInstrument(item, index);
                if (metadata.exchange() == Exchange.SSE || metadata.exchange() == Exchange.SZSE) {
                    result.add(metadata);
                }
            }
        }
        return result;
    }

    @Override
    public List<ProviderDailyBar> fetchDailyBars(List<MarketSymbol> symbols, LocalDate startDate, LocalDate endDate) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        String tsCodes = symbols.stream().map(MarketSymbol::tushareCode).collect(Collectors.joining(","));
        TushareResponse response = httpClient.post("daily", Map.of(
                "ts_code", tsCodes,
                "start_date", TUSHARE_DATE.format(startDate),
                "end_date", TUSHARE_DATE.format(endDate)
        ), DAILY_FIELDS);
        Map<String, Integer> index = indexFields(response.fields(), requiredFields(DAILY_FIELDS));
        List<ProviderDailyBar> bars = new ArrayList<>();
        for (JsonNode item : response.items()) {
            bars.add(mapDailyBar(item, index));
        }
        return bars;
    }

    @Override
    public String providerName() {
        return "TUSHARE";
    }

    private ProviderInstrumentMetadata mapInstrument(JsonNode item, Map<String, Integer> index) {
        String tsCode = text(item, index, "ts_code");
        MarketSymbol symbol = parseTushareCode(tsCode);
        return new ProviderInstrumentMetadata(
                symbol,
                text(item, index, "name"),
                exchangeFromProvider(text(item, index, "exchange")),
                currencyFromProvider(text(item, index, "curr_type")),
                text(item, index, "list_status"),
                parseOptionalDate(text(item, index, "list_date"))
        );
    }

    private ProviderDailyBar mapDailyBar(JsonNode item, Map<String, Integer> index) {
        MarketSymbol symbol = parseTushareCode(text(item, index, "ts_code"));
        BigDecimal amountCny = decimal(item, index, "amount").multiply(THOUSAND);
        return new ProviderDailyBar(
                symbol,
                parseRequiredDate(text(item, index, "trade_date"), "trade_date"),
                decimal(item, index, "open"),
                decimal(item, index, "high"),
                decimal(item, index, "low"),
                decimal(item, index, "close"),
                decimal(item, index, "pre_close"),
                decimal(item, index, "change"),
                decimal(item, index, "pct_chg"),
                decimal(item, index, "vol"),
                amountCny
        );
    }

    private Map<String, Integer> indexFields(JsonNode fields, List<String> requiredFields) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            index.put(fields.get(i).asText(), i);
        }
        for (String requiredField : requiredFields) {
            if (!index.containsKey(requiredField)) {
                throw new MarketDataProviderException(MarketDataProviderErrorType.INVALID_RESPONSE,
                        "Market data provider response missing field: " + requiredField);
            }
        }
        return index;
    }

    private List<String> requiredFields(String fields) {
        return List.of(fields.split(","));
    }

    private String text(JsonNode item, Map<String, Integer> index, String field) {
        JsonNode value = item.get(index.get(field));
        if (value == null || value.isNull()) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.INVALID_RESPONSE,
                    "Market data provider response has null field: " + field);
        }
        return value.asText();
    }

    private BigDecimal decimal(JsonNode item, Map<String, Integer> index, String field) {
        try {
            return new BigDecimal(text(item, index, field));
        } catch (NumberFormatException ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.INVALID_RESPONSE,
                    "Market data provider response has invalid number: " + field, ex);
        }
    }

    private MarketSymbol parseTushareCode(String tsCode) {
        if (tsCode.endsWith(".SH")) {
            return new MarketSymbol(tsCode.substring(0, 6), Exchange.SSE);
        }
        if (tsCode.endsWith(".SZ")) {
            return new MarketSymbol(tsCode.substring(0, 6), Exchange.SZSE);
        }
        throw new MarketDataProviderException(MarketDataProviderErrorType.INVALID_RESPONSE,
                "Unsupported provider code: " + tsCode);
    }

    private Exchange exchangeFromProvider(String exchange) {
        return switch (exchange) {
            case "SSE" -> Exchange.SSE;
            case "SZSE" -> Exchange.SZSE;
            default -> throw new MarketDataProviderException(MarketDataProviderErrorType.INVALID_RESPONSE,
                    "Unsupported provider exchange: " + exchange);
        };
    }

    private String currencyFromProvider(String currency) {
        if (currency == null || currency.isBlank()) {
            return "CNY";
        }
        return currency.equalsIgnoreCase("CNY") ? "CNY" : currency.toUpperCase();
    }

    private LocalDate parseOptionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequiredDate(value, "list_date");
    }

    private LocalDate parseRequiredDate(String value, String field) {
        try {
            return LocalDate.parse(value, TUSHARE_DATE);
        } catch (DateTimeParseException ex) {
            throw new MarketDataProviderException(MarketDataProviderErrorType.INVALID_RESPONSE,
                    "Market data provider response has invalid date: " + field, ex);
        }
    }
}
