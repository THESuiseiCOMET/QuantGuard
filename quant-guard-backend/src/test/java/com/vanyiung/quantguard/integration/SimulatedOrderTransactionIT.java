package com.vanyiung.quantguard.integration;

import com.vanyiung.quantguard.dto.CreateSimulatedOrderRequest;
import com.vanyiung.quantguard.enums.AccountType;
import com.vanyiung.quantguard.enums.OrderSide;
import com.vanyiung.quantguard.enums.OrderStatus;
import com.vanyiung.quantguard.enums.OrderType;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.service.SimulatedOrderExecutionService;
import com.vanyiung.quantguard.service.SimulatedOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class SimulatedOrderTransactionIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("quant_guard")
            .withUsername("quantguard_test")
            .withPassword("quantguard_test")
            .withCommand("--log-bin-trust-function-creators=1");

    private static boolean schemaInitialized;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SimulatedOrderService simulatedOrderService;

    @Autowired
    private SimulatedOrderExecutionService simulatedOrderExecutionService;

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @BeforeEach
    void setUpDatabase() throws Exception {
        initializeSchemaIfNeeded();
        dropFailureTriggers();
        clearTables();
    }

    @Test
    void buyOrderFreezeLedgerFailureRollsBackAccountAndOrder() {
        seedBaseData(AccountType.LONG_TERM);
        createCashLedgerFailureTrigger("BUY_ORDER_FREEZE");

        assertThrowsBusinessRuleOrDataAccess(() -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        assertEquals(0, countRows("simulated_order"));
        assertEquals(new BigDecimal("100000.0000"), accountAvailableCash());
        assertEquals(new BigDecimal("0.0000"), accountFrozenCash());
    }

    @Test
    void buyExecutionTradeInsertFailureRollsBackOrderCashPositionAndTrade() {
        seedOpenBuyOrder();
        createTradeFailureTrigger();

        assertThrowsBusinessRuleOrDataAccess(() -> simulatedOrderExecutionService.execute(100L));

        assertEquals("OPEN", orderStatus(100L));
        assertEquals(new BigDecimal("80000.0000"), accountAvailableCash());
        assertEquals(new BigDecimal("2000.0000"), accountFrozenCash());
        assertEquals(0, countRows("portfolio_position"));
        assertEquals(0, countRows("simulated_trade"));
        assertEquals(0, countCashLedger("BUY_SETTLEMENT"));
    }

    @Test
    void buyExecutionSettlementLedgerFailureRollsBackOrderCashPositionAndTrade() {
        seedOpenBuyOrder();
        createCashLedgerFailureTrigger("BUY_SETTLEMENT");

        assertThrowsBusinessRuleOrDataAccess(() -> simulatedOrderExecutionService.execute(100L));

        assertEquals("OPEN", orderStatus(100L));
        assertEquals(new BigDecimal("80000.0000"), accountAvailableCash());
        assertEquals(new BigDecimal("2000.0000"), accountFrozenCash());
        assertEquals(0, countRows("portfolio_position"));
        assertEquals(0, countRows("simulated_trade"));
        assertEquals(0, countCashLedger("BUY_SETTLEMENT"));
    }

    @Test
    void sellExecutionProceedsLedgerFailureRollsBackOrderCashPositionAndTrade() {
        seedOpenSellOrder();
        createCashLedgerFailureTrigger("SELL_PROCEEDS");

        assertThrowsBusinessRuleOrDataAccess(() -> simulatedOrderExecutionService.execute(100L));

        assertEquals("OPEN", orderStatus(100L));
        assertEquals(new BigDecimal("80000.0000"), accountAvailableCash());
        assertEquals(new BigDecimal("0.0000"), accountFrozenCash());
        assertEquals(new BigDecimal("500.000000"), positionTotalQuantity());
        assertEquals(new BigDecimal("300.000000"), positionAvailableQuantity());
        assertEquals(new BigDecimal("200.000000"), positionFrozenQuantity());
        assertEquals(0, countRows("simulated_trade"));
        assertEquals(0, countCashLedger("SELL_PROCEEDS"));
    }

    @Test
    void concurrentExecuteSameOpenOrderOnlySucceedsOnce() throws Exception {
        seedOpenBuyOrder();

        List<Throwable> failures = runConcurrently(
                () -> simulatedOrderExecutionService.execute(100L),
                () -> simulatedOrderExecutionService.execute(100L));

        assertEquals(1, failures.size());
        assertEquals("FILLED", orderStatus(100L));
        assertEquals(1, countRows("simulated_trade"));
        assertEquals(1, countCashLedger("BUY_SETTLEMENT"));
        assertEquals(new BigDecimal("80000.0000"), accountAvailableCash());
        assertEquals(new BigDecimal("0.0000"), accountFrozenCash());
        assertEquals(new BigDecimal("200.000000"), positionTotalQuantity());
    }

    @Test
    void concurrentExecuteAndCancelSameOpenOrderOnlyOneOperationWins() throws Exception {
        seedOpenBuyOrder();

        List<Throwable> failures = runConcurrently(
                () -> simulatedOrderExecutionService.execute(100L),
                () -> simulatedOrderExecutionService.cancel(100L));

        assertEquals(1, failures.size());
        String finalStatus = orderStatus(100L);
        assertTrue(finalStatus.equals("FILLED") || finalStatus.equals("CANCELED"));
        assertEquals(1, countRows("simulated_order"));
        assertTrue(accountAvailableCash().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(accountFrozenCash().compareTo(BigDecimal.ZERO) >= 0);

        if (finalStatus.equals("FILLED")) {
            assertEquals(1, countRows("simulated_trade"));
            assertEquals(1, countCashLedger("BUY_SETTLEMENT"));
            assertEquals(0, countCashLedger("BUY_CANCEL_RELEASE"));
        } else {
            assertEquals(0, countRows("simulated_trade"));
            assertEquals(0, countCashLedger("BUY_SETTLEMENT"));
            assertEquals(1, countCashLedger("BUY_CANCEL_RELEASE"));
        }
    }

    private void initializeSchemaIfNeeded() throws Exception {
        if (schemaInitialized) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            for (String script : List.of(
                    "db/create_database.sql",
                    "db/migration/V1__create_simulation_account.sql",
                    "db/migration/V2__create_investment_portfolio.sql",
                    "db/migration/V3__create_instrument_and_position_tables.sql",
                    "db/migration/V4__create_risk_config_and_simulated_order_tables.sql",
                    "db/migration/V5__create_trade_and_cash_ledger_tables.sql",
                    "db/migration/V5_1__harden_cash_ledger.sql")) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(script));
            }
        }
        schemaInitialized = true;
    }

    private void seedOpenBuyOrder() {
        seedBaseData(AccountType.LONG_TERM);
        jdbcTemplate.update("UPDATE simulation_account SET available_cash = 80000.0000, frozen_cash = 2000.0000 WHERE id = 1");
        jdbcTemplate.update("""
                INSERT INTO simulated_order
                    (id, order_no, account_id, portfolio_id, instrument_id, side, order_type, requested_quantity,
                     limit_price, frozen_amount, frozen_quantity, status, created_at, updated_at, version)
                VALUES
                    (100, 'SO_TEST_BUY_100', 1, 1, 1, 'BUY', 'LIMIT', 200.000000,
                     10.0000, 2000.0000, 0.000000, 'OPEN', NOW(6), NOW(6), 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO cash_ledger
                    (account_id, portfolio_id, order_id, trade_id, entry_type, available_cash_delta, frozen_cash_delta,
                     available_cash_after, frozen_cash_after, description, created_at)
                VALUES
                    (1, 1, 100, NULL, 'BUY_ORDER_FREEZE', -2000.0000, 2000.0000,
                     80000.0000, 2000.0000, 'seed BUY order cash freeze', NOW(6))
                """);
    }

    private void seedOpenSellOrder() {
        seedBaseData(AccountType.LONG_TERM);
        jdbcTemplate.update("UPDATE simulation_account SET available_cash = 80000.0000, frozen_cash = 0.0000 WHERE id = 1");
        jdbcTemplate.update("""
                INSERT INTO portfolio_position
                    (id, portfolio_id, instrument_id, total_quantity, available_quantity, frozen_quantity, average_cost,
                     created_at, updated_at, version)
                VALUES
                    (1, 1, 1, 500.000000, 300.000000, 200.000000, 5.0000, NOW(6), NOW(6), 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO simulated_order
                    (id, order_no, account_id, portfolio_id, instrument_id, side, order_type, requested_quantity,
                     limit_price, frozen_amount, frozen_quantity, status, created_at, updated_at, version)
                VALUES
                    (100, 'SO_TEST_SELL_100', 1, 1, 1, 'SELL', 'LIMIT', 200.000000,
                     10.0000, 0.0000, 200.000000, 'OPEN', NOW(6), NOW(6), 0)
                """);
    }

    private void seedBaseData(AccountType accountType) {
        jdbcTemplate.update("""
                INSERT INTO simulation_account
                    (id, account_name, account_type, initial_cash, available_cash, frozen_cash, status, created_at, updated_at, version)
                VALUES
                    (1, 'Integration Account', ?, 100000.0000, 100000.0000, 0.0000, 'ACTIVE', NOW(6), NOW(6), 0)
                """, accountType.name());
        jdbcTemplate.update("""
                INSERT INTO investment_portfolio
                    (id, account_id, portfolio_name, portfolio_type, description, status, created_at, updated_at)
                VALUES
                    (1, 1, 'Integration Portfolio', ?, NULL, 'ACTIVE', NOW(6), NOW(6))
                """, accountType.name());
        jdbcTemplate.update("""
                INSERT INTO security_instrument
                    (id, symbol, exchange, instrument_name, asset_type, currency, lot_size, status, created_at, updated_at)
                VALUES
                    (1, '510300', 'SSE', 'CSI 300 ETF', 'ETF', 'CNY', 100.000000, 'ACTIVE', NOW(6), NOW(6))
                """);
        jdbcTemplate.update("""
                INSERT INTO portfolio_risk_config
                    (id, portfolio_id, max_single_order_amount, max_position_ratio, allow_buy, allow_sell, status,
                     created_at, updated_at, version)
                VALUES
                    (1, 1, 50000.0000, 0.500000, TRUE, TRUE, 'ACTIVE', NOW(6), NOW(6), 0)
                """);
    }

    private void clearTables() {
        jdbcTemplate.update("DELETE FROM cash_ledger");
        jdbcTemplate.update("DELETE FROM simulated_trade");
        jdbcTemplate.update("DELETE FROM simulated_order");
        jdbcTemplate.update("DELETE FROM portfolio_risk_config");
        jdbcTemplate.update("DELETE FROM portfolio_position");
        jdbcTemplate.update("DELETE FROM security_instrument");
        jdbcTemplate.update("DELETE FROM investment_portfolio");
        jdbcTemplate.update("DELETE FROM simulation_account");
    }

    private void dropFailureTriggers() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_cash_ledger");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_simulated_trade");
    }

    private void createCashLedgerFailureTrigger(String entryType) {
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_fail_cash_ledger
                BEFORE INSERT ON cash_ledger
                FOR EACH ROW
                BEGIN
                    IF NEW.entry_type = '%s' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced cash ledger failure';
                    END IF;
                END
                """.formatted(entryType));
    }

    private void createTradeFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_fail_simulated_trade
                BEFORE INSERT ON simulated_trade
                FOR EACH ROW
                BEGIN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced trade failure';
                END
                """);
    }

    private List<Throwable> runConcurrently(Runnable first, Runnable second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var failures = new java.util.concurrent.CopyOnWriteArrayList<Throwable>();
        for (Runnable task : List.of(first, second)) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    task.run();
                } catch (Throwable ex) {
                    failures.add(ex);
                }
            });
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        return failures;
    }

    private void assertThrowsBusinessRuleOrDataAccess(Runnable runnable) {
        try {
            runnable.run();
            fail("Expected an exception");
        } catch (BusinessRuleException | org.springframework.dao.DataAccessException ex) {
            // Expected: both exception types must escape the transaction and trigger rollback.
        }
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private int countCashLedger(String entryType) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cash_ledger WHERE entry_type = ?", Integer.class, entryType);
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM simulated_order WHERE id = ?", String.class, orderId);
    }

    private BigDecimal accountAvailableCash() {
        return jdbcTemplate.queryForObject("SELECT available_cash FROM simulation_account WHERE id = 1", BigDecimal.class);
    }

    private BigDecimal accountFrozenCash() {
        return jdbcTemplate.queryForObject("SELECT frozen_cash FROM simulation_account WHERE id = 1", BigDecimal.class);
    }

    private BigDecimal positionTotalQuantity() {
        return jdbcTemplate.queryForObject("""
                SELECT total_quantity
                FROM portfolio_position
                WHERE portfolio_id = 1 AND instrument_id = 1
                """, BigDecimal.class);
    }

    private BigDecimal positionAvailableQuantity() {
        return jdbcTemplate.queryForObject("""
                SELECT available_quantity
                FROM portfolio_position
                WHERE portfolio_id = 1 AND instrument_id = 1
                """, BigDecimal.class);
    }

    private BigDecimal positionFrozenQuantity() {
        return jdbcTemplate.queryForObject("""
                SELECT frozen_quantity
                FROM portfolio_position
                WHERE portfolio_id = 1 AND instrument_id = 1
                """, BigDecimal.class);
    }
}
