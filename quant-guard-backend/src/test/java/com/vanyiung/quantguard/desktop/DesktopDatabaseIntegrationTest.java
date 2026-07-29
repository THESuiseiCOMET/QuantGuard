package com.vanyiung.quantguard.desktop;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vanyiung.quantguard.QuantGuardBackendApplication;
import com.vanyiung.quantguard.dto.CreateInvestmentPortfolioRequest;
import com.vanyiung.quantguard.dto.CreateSecurityInstrumentRequest;
import com.vanyiung.quantguard.dto.CreateSimulatedOrderRequest;
import com.vanyiung.quantguard.dto.CreateSimulationAccountRequest;
import com.vanyiung.quantguard.dto.ExecuteSimulatedOrderResponse;
import com.vanyiung.quantguard.dto.InvestmentPortfolioResponse;
import com.vanyiung.quantguard.dto.SecurityInstrumentResponse;
import com.vanyiung.quantguard.dto.SimulationAccountResponse;
import com.vanyiung.quantguard.entity.InstrumentDailyPrice;
import com.vanyiung.quantguard.enums.AccountType;
import com.vanyiung.quantguard.enums.AdjustmentType;
import com.vanyiung.quantguard.enums.AssetType;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.enums.OrderSide;
import com.vanyiung.quantguard.enums.OrderStatus;
import com.vanyiung.quantguard.enums.OrderType;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.mapper.InstrumentDailyPriceMapper;
import com.vanyiung.quantguard.service.CashLedgerService;
import com.vanyiung.quantguard.service.InvestmentPortfolioService;
import com.vanyiung.quantguard.service.PortfolioPositionService;
import com.vanyiung.quantguard.service.SecurityInstrumentService;
import com.vanyiung.quantguard.service.SimulatedOrderExecutionService;
import com.vanyiung.quantguard.service.SimulatedOrderService;
import com.vanyiung.quantguard.service.SimulatedTradeService;
import com.vanyiung.quantguard.service.SimulationAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopDatabaseIntegrationTest {

    private static final List<String> BUSINESS_TABLES = List.of(
            "simulation_account",
            "investment_portfolio",
            "security_instrument",
            "portfolio_position",
            "portfolio_risk_config",
            "simulated_order",
            "simulated_trade",
            "cash_ledger",
            "instrument_daily_price",
            "market_data_sync_log"
    );

    @TempDir
    Path tempDirectory;

    @AfterEach
    void clearDesktopDataOverride() {
        System.clearProperty("QUANTGUARD_DATA_DIR");
        System.clearProperty("QUANTGUARD_DESKTOP_FILE_LOGGING_ENABLED");
    }

    @Test
    void firstDesktopStartCreatesFileDatabaseAndFlywaySchema() {
        Path userData = tempDirectory.resolve("first-start");

        try (ConfigurableApplicationContext context = startDesktop(userData)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            for (String table : BUSINESS_TABLES) {
                assertEquals(1, countTable(jdbc, table), "Missing desktop table: " + table);
            }
            assertEquals(1, countTable(jdbc, "flyway_schema_history"));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                    Integer.class));
            assertTrue(context.getEnvironment().getProperty("spring.datasource.url", "")
                    .contains(userData.resolve("data").resolve("quantguard").toString().replace('\\', '/')));
        }

        assertTrue(Files.exists(userData.resolve("data").resolve("quantguard.mv.db")));
        assertFalse(Files.exists(Path.of("quantguard.mv.db")));
    }

    @Test
    void restartPreservesCoreTradingAndMarketData() {
        Path userData = tempDirectory.resolve("restart");
        DesktopSnapshot snapshot;

        try (ConfigurableApplicationContext context = startDesktop(userData)) {
            snapshot = createTradingSnapshot(context);
        }

        try (ConfigurableApplicationContext context = startDesktop(userData)) {
            SimulationAccountService accounts = context.getBean(SimulationAccountService.class);
            InvestmentPortfolioService portfolios = context.getBean(InvestmentPortfolioService.class);
            SecurityInstrumentService instruments = context.getBean(SecurityInstrumentService.class);
            InstrumentDailyPriceMapper prices = context.getBean(InstrumentDailyPriceMapper.class);
            PortfolioPositionService positions = context.getBean(PortfolioPositionService.class);
            SimulatedTradeService trades = context.getBean(SimulatedTradeService.class);
            CashLedgerService ledger = context.getBean(CashLedgerService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            assertEquals("desktop-account", accounts.getById(snapshot.accountId()).accountName());
            assertEquals("desktop-portfolio", portfolios.getById(snapshot.portfolioId()).portfolioName());
            assertEquals("600519", instruments.getById(snapshot.stockId()).symbol());
            assertEquals(1, prices.selectCount(Wrappers.<InstrumentDailyPrice>lambdaQuery()
                    .eq(InstrumentDailyPrice::getInstrumentId, snapshot.stockId())));
            assertEquals(1, prices.selectCount(Wrappers.<InstrumentDailyPrice>lambdaQuery()
                    .eq(InstrumentDailyPrice::getInstrumentId, snapshot.indexId())));
            assertEquals(new BigDecimal("100.000000"),
                    positions.listByPortfolioId(snapshot.portfolioId()).getFirst().totalQuantity());
            assertEquals(1, trades.listByPortfolioId(snapshot.portfolioId()).size());
            assertEquals(2, ledger.listByAccountId(snapshot.accountId()).size());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                    Integer.class));
        }
    }

    @Test
    void desktopSchemaKeepsInstrumentAndFirstBarConstraints() {
        Path userData = tempDirectory.resolve("constraints");

        try (ConfigurableApplicationContext context = startDesktop(userData)) {
            SecurityInstrumentService instruments = context.getBean(SecurityInstrumentService.class);
            SimulatedOrderService orders = context.getBean(SimulatedOrderService.class);
            SimulationAccountService accounts = context.getBean(SimulationAccountService.class);
            InvestmentPortfolioService portfolios = context.getBean(InvestmentPortfolioService.class);
            InstrumentDailyPriceMapper prices = context.getBean(InstrumentDailyPriceMapper.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            SimulationAccountResponse account = accounts.create(new CreateSimulationAccountRequest(
                    "constraint-account", AccountType.LONG_TERM, new BigDecimal("10000.00")));
            InvestmentPortfolioResponse portfolio = portfolios.create(new CreateInvestmentPortfolioRequest(
                    account.id(), "constraint-portfolio", AccountType.LONG_TERM, null));
            SecurityInstrumentResponse stock = instruments.create(instrument(
                    "510300", Exchange.SSE, "ETF", AssetType.ETF, "100.000000"));
            SecurityInstrumentResponse index = instruments.create(instrument(
                    "000688", Exchange.SSE, "Index", AssetType.INDEX, "1.000000"));

            prices.insert(dailyPrice(index.id(), LocalDate.of(2026, 7, 24)));
            InstrumentDailyPrice firstBar = prices.selectOne(Wrappers.<InstrumentDailyPrice>lambdaQuery()
                    .eq(InstrumentDailyPrice::getInstrumentId, index.id()));
            assertNotNull(firstBar);
            assertNull(firstBar.getPreviousClose());
            assertNull(firstBar.getPriceChange());
            assertNull(firstBar.getPercentageChange());

            assertThrows(BusinessRuleException.class, () -> orders.create(new CreateSimulatedOrderRequest(
                    account.id(), portfolio.id(), index.id(), OrderSide.BUY, OrderType.LIMIT,
                    BigDecimal.ONE, new BigDecimal("10.0000"))));

            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO security_instrument
                        (symbol, exchange, instrument_name, asset_type, currency, lot_size, status,
                         created_at, updated_at)
                    VALUES ('INVALID', 'SSE', 'Invalid type', 'BOND', 'CNY', 1, 'ACTIVE',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """));
            assertEquals(AssetType.ETF, instruments.getById(stock.id()).assetType());
        }
    }

    private DesktopSnapshot createTradingSnapshot(ConfigurableApplicationContext context) {
        SimulationAccountService accounts = context.getBean(SimulationAccountService.class);
        InvestmentPortfolioService portfolios = context.getBean(InvestmentPortfolioService.class);
        SecurityInstrumentService instruments = context.getBean(SecurityInstrumentService.class);
        InstrumentDailyPriceMapper prices = context.getBean(InstrumentDailyPriceMapper.class);
        SimulatedOrderService orders = context.getBean(SimulatedOrderService.class);
        SimulatedOrderExecutionService executions = context.getBean(SimulatedOrderExecutionService.class);

        SimulationAccountResponse account = accounts.create(new CreateSimulationAccountRequest(
                "desktop-account", AccountType.LONG_TERM, new BigDecimal("10000.00")));
        InvestmentPortfolioResponse portfolio = portfolios.create(new CreateInvestmentPortfolioRequest(
                account.id(), "desktop-portfolio", AccountType.LONG_TERM, "desktop persistence test"));
        SecurityInstrumentResponse stock = instruments.create(instrument(
                "600519", Exchange.SSE, "Desktop Stock", AssetType.STOCK, "100.000000"));
        SecurityInstrumentResponse index = instruments.create(instrument(
                "000688", Exchange.SSE, "Desktop Index", AssetType.INDEX, "1.000000"));
        prices.insert(dailyPrice(stock.id(), LocalDate.of(2026, 7, 24)));
        prices.insert(dailyPrice(index.id(), LocalDate.of(2026, 7, 24)));

        var order = orders.create(new CreateSimulatedOrderRequest(
                account.id(), portfolio.id(), stock.id(), OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100.000000"), new BigDecimal("10.0000")));
        ExecuteSimulatedOrderResponse execution = executions.execute(order.id());

        assertEquals(OrderStatus.FILLED, execution.order().status());
        assertEquals(new BigDecimal("1000.0000"), execution.trade().tradeAmount());
        return new DesktopSnapshot(account.id(), portfolio.id(), stock.id(), index.id());
    }

    private ConfigurableApplicationContext startDesktop(Path userData) {
        System.setProperty("QUANTGUARD_DATA_DIR", userData.toString());
        System.setProperty("QUANTGUARD_DESKTOP_FILE_LOGGING_ENABLED", "false");
        return new SpringApplicationBuilder(QuantGuardBackendApplication.class)
                .profiles("desktop")
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "quantguard.market-data.auto-sync-enabled=false"
                )
                .run();
    }

    private CreateSecurityInstrumentRequest instrument(
            String symbol,
            Exchange exchange,
            String name,
            AssetType assetType,
            String lotSize
    ) {
        return new CreateSecurityInstrumentRequest(
                symbol, exchange, name, assetType, "CNY", new BigDecimal(lotSize));
    }

    private InstrumentDailyPrice dailyPrice(Long instrumentId, LocalDate tradeDate) {
        InstrumentDailyPrice price = new InstrumentDailyPrice();
        price.setInstrumentId(instrumentId);
        price.setTradeDate(tradeDate);
        price.setOpenPrice(new BigDecimal("10.0000"));
        price.setHighPrice(new BigDecimal("11.0000"));
        price.setLowPrice(new BigDecimal("9.0000"));
        price.setClosePrice(new BigDecimal("10.5000"));
        price.setPreviousClose(null);
        price.setPriceChange(null);
        price.setPercentageChange(null);
        price.setVolumeLots(new BigDecimal("1000.0000"));
        price.setAmountCny(new BigDecimal("1050000.0000"));
        price.setAdjustmentType(AdjustmentType.NONE);
        price.setDataSource("DESKTOP_TEST");
        price.setCreatedAt(LocalDateTime.now());
        price.setUpdatedAt(LocalDateTime.now());
        price.setVersion(0);
        return price;
    }

    private int countTable(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """, Integer.class, tableName);
    }

    private record DesktopSnapshot(Long accountId, Long portfolioId, Long stockId, Long indexId) {
    }
}
