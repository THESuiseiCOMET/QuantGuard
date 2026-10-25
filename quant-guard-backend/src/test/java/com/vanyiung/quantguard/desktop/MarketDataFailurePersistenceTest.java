package com.vanyiung.quantguard.desktop;

import com.sun.net.httpserver.HttpServer;
import com.vanyiung.quantguard.QuantGuardBackendApplication;
import com.vanyiung.quantguard.dto.CreateSecurityInstrumentRequest;
import com.vanyiung.quantguard.dto.DailyPriceSyncRequest;
import com.vanyiung.quantguard.enums.AssetType;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.service.MarketDataService;
import com.vanyiung.quantguard.service.SecurityInstrumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketDataFailurePersistenceTest {
    @TempDir
    Path directory;

    @AfterEach
    void clearOverrides() {
        System.clearProperty("QUANTGUARD_DATA_DIR");
        System.clearProperty("QUANTGUARD_DESKTOP_FILE_LOGGING_ENABLED");
    }

    @Test
    void providerTimeoutIsSavedAndSurvivesDatabaseRestart() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/daily-bars", exchange -> {
            byte[] body = "{\"code\":\"TIMEOUT\",\"message\":\"Vendor request timed out\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(504, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            try (var context = start(baseUrl)) {
                var instrument = context.getBean(SecurityInstrumentService.class).create(
                        new CreateSecurityInstrumentRequest("600519", Exchange.SSE, "Test stock", AssetType.STOCK,
                                "CNY", new BigDecimal("100")));
                var result = context.getBean(MarketDataService.class).syncInstrumentDailyPrices(instrument.id(),
                        new DailyPriceSyncRequest(null, LocalDate.of(2026, 10, 23), false), "MANUAL");
                assertEquals("FAILED", result.status());
                assertEquals("Vendor request timed out", result.message());
            }
            try (var context = start(baseUrl)) {
                var jdbc = context.getBean(JdbcTemplate.class);
                assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM market_data_sync_log", String.class));
                assertEquals("Vendor request timed out", jdbc.queryForObject("SELECT error_message FROM market_data_sync_log", String.class));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM instrument_daily_price", Integer.class));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM security_instrument", Integer.class));
            }
        } finally {
            server.stop(0);
        }
    }

    private ConfigurableApplicationContext start(String baseUrl) {
        System.setProperty("QUANTGUARD_DATA_DIR", directory.toString());
        System.setProperty("QUANTGUARD_DESKTOP_FILE_LOGGING_ENABLED", "false");
        return new SpringApplicationBuilder(QuantGuardBackendApplication.class).profiles("desktop")
                .web(WebApplicationType.NONE).run("--spring.main.banner-mode=off",
                        "--quantguard.market-data.auto-sync-enabled=false",
                        "--quantguard.market-data.python.base-url=" + baseUrl);
    }
}
