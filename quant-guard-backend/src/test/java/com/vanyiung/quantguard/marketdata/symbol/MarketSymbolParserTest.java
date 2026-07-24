package com.vanyiung.quantguard.marketdata.symbol;

import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSymbolParserTest {

    private final MarketSymbolParser parser = new MarketSymbolParser();

    @Test
    void parsesSupportedSingleFormats() {
        assertSymbol("600519", "600519", Exchange.SSE, "600519.SH");
        assertSymbol("600519.SH", "600519", Exchange.SSE, "600519.SH");
        assertSymbol("SSE:600519", "600519", Exchange.SSE, "600519.SH");
        assertSymbol("000001", "000001", Exchange.SZSE, "000001.SZ");
        assertSymbol("000001.SZ", "000001", Exchange.SZSE, "000001.SZ");
        assertSymbol("SZSE:000001", "000001", Exchange.SZSE, "000001.SZ");
        assertSymbol("sse:601318", "601318", Exchange.SSE, "601318.SH");
    }

    @Test
    void parsesDelimitedBatchDeduplicatesAndKeepsOrder() {
        List<MarketSymbolParseResult> result = parser.parseInputs(List.of("600519,000001.SZ，SSE:601318\n600519; 300750"));

        assertEquals(4, result.size());
        assertEquals("600519.SH", result.get(0).symbol().canonicalCode());
        assertEquals("000001.SZ", result.get(1).symbol().canonicalCode());
        assertEquals("601318.SH", result.get(2).symbol().canonicalCode());
        assertEquals("300750.SZ", result.get(3).symbol().canonicalCode());
    }

    @Test
    void rejectsInvalidFormatsWithoutThrowingForItemLevelErrors() {
        assertInvalid("12345", "6 digits");
        assertInvalid("999999", "Exchange cannot be inferred");
        assertInvalid("600519.HK", "Only SSE and SZSE");
        assertInvalid("600519.US", "Only SSE and SZSE");
        assertInvalid("430001.BJ", "Only SSE and SZSE");
        assertInvalid("600519.XX", "Unknown exchange suffix");
    }

    @Test
    void rejectsEmptyAndOversizedBatch() {
        assertThrows(BusinessRuleException.class, () -> parser.parseInputs(List.of("  ")));
        List<String> tooMany = IntStream.range(0, 51).mapToObj(i -> String.format("600%03d", i)).toList();
        assertThrows(BusinessRuleException.class, () -> parser.parseInputs(tooMany));
    }

    private void assertSymbol(String input, String symbol, Exchange exchange, String canonicalCode) {
        MarketSymbolParseResult result = parser.parseOne(input);
        assertTrue(result.valid());
        assertEquals(symbol, result.symbol().symbol());
        assertEquals(exchange, result.symbol().exchange());
        assertEquals(canonicalCode, result.symbol().canonicalCode());
    }

    private void assertInvalid(String input, String messageFragment) {
        MarketSymbolParseResult result = parser.parseOne(input);
        assertFalse(result.valid());
        assertTrue(result.message().contains(messageFragment));
    }
}
