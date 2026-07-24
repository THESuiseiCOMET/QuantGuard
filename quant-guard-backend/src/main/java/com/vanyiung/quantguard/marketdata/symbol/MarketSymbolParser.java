package com.vanyiung.quantguard.marketdata.symbol;

import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class MarketSymbolParser {

    private static final int MAX_SYMBOLS = 50;
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[,，;；\\s]+");
    private static final Pattern A_SHARE_SYMBOL = Pattern.compile("\\d{6}");

    public List<MarketSymbolParseResult> parseInputs(List<String> inputs) {
        List<String> tokens = tokenize(inputs);
        if (tokens.isEmpty()) {
            throw new BusinessRuleException("codes must not be empty");
        }
        if (tokens.size() > MAX_SYMBOLS) {
            throw new BusinessRuleException("A single import batch supports at most 50 codes");
        }

        LinkedHashMap<String, MarketSymbolParseResult> deduplicated = new LinkedHashMap<>();
        for (String token : tokens) {
            MarketSymbolParseResult result = parseOne(token);
            String key = result.valid() ? result.symbol().canonicalCode() : "invalid:" + token.toUpperCase(Locale.ROOT);
            deduplicated.putIfAbsent(key, result);
        }
        return new ArrayList<>(deduplicated.values());
    }

    public MarketSymbolParseResult parseOne(String input) {
        String original = input == null ? "" : input;
        String normalized = original.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return MarketSymbolParseResult.invalid(original, "Code must not be empty");
        }

        String symbol;
        Exchange exchange;
        if (normalized.startsWith("SSE:")) {
            symbol = normalized.substring("SSE:".length());
            exchange = Exchange.SSE;
        } else if (normalized.startsWith("SZSE:")) {
            symbol = normalized.substring("SZSE:".length());
            exchange = Exchange.SZSE;
        } else if (normalized.endsWith(".SH")) {
            symbol = normalized.substring(0, normalized.length() - 3);
            exchange = Exchange.SSE;
        } else if (normalized.endsWith(".SZ")) {
            symbol = normalized.substring(0, normalized.length() - 3);
            exchange = Exchange.SZSE;
        } else if (normalized.endsWith(".BJ") || normalized.endsWith(".HK") || normalized.endsWith(".US")) {
            return MarketSymbolParseResult.invalid(original, "Only SSE and SZSE A-share stocks are supported in this phase");
        } else if (normalized.contains(".")) {
            return MarketSymbolParseResult.invalid(original, "Unknown exchange suffix");
        } else {
            symbol = normalized;
            if (symbol.chars().allMatch(Character::isDigit) && !A_SHARE_SYMBOL.matcher(symbol).matches()) {
                return MarketSymbolParseResult.invalid(original, "A-share code must be exactly 6 digits");
            }
            exchange = inferExchange(symbol);
            if (exchange == null) {
                return MarketSymbolParseResult.invalid(original,
                        "Exchange cannot be inferred. Please use 600519.SH or SSE:600519.");
            }
        }

        if (!A_SHARE_SYMBOL.matcher(symbol).matches()) {
            return MarketSymbolParseResult.invalid(original, "A-share code must be exactly 6 digits");
        }
        if (symbol.startsWith("8") || symbol.startsWith("4")) {
            return MarketSymbolParseResult.invalid(original, "Beijing Stock Exchange codes are not supported in this phase");
        }
        return MarketSymbolParseResult.valid(original, new MarketSymbol(symbol, exchange));
    }

    private List<String> tokenize(List<String> inputs) {
        if (inputs == null) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String input : inputs) {
            if (input == null) {
                continue;
            }
            for (String token : SPLIT_PATTERN.split(input)) {
                if (!token.isBlank()) {
                    tokens.add(token);
                }
            }
        }
        return tokens;
    }

    private Exchange inferExchange(String symbol) {
        if (symbol.startsWith("60") || symbol.startsWith("68") || symbol.startsWith("90")) {
            return Exchange.SSE;
        }
        if (symbol.startsWith("00") || symbol.startsWith("20") || symbol.startsWith("30")) {
            return Exchange.SZSE;
        }
        return null;
    }
}
