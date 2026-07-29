package com.vanyiung.quantguard.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class MarketDataSchemaMigrationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("quant_guard")
            .withUsername("quantguard_test")
            .withPassword("quantguard_test");

    private Connection connection;

    @BeforeEach
    void resetSchema() throws Exception {
        connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        connection.createStatement().execute("DROP TABLE IF EXISTS cash_ledger");
        connection.createStatement().execute("DROP TABLE IF EXISTS simulated_trade");
        connection.createStatement().execute("DROP TABLE IF EXISTS simulated_order");
        connection.createStatement().execute("DROP TABLE IF EXISTS portfolio_risk_config");
        connection.createStatement().execute("DROP TABLE IF EXISTS market_data_sync_log");
        connection.createStatement().execute("DROP TABLE IF EXISTS instrument_daily_price");
        connection.createStatement().execute("DROP TABLE IF EXISTS portfolio_position");
        connection.createStatement().execute("DROP TABLE IF EXISTS security_instrument");
        connection.createStatement().execute("DROP TABLE IF EXISTS investment_portfolio");
        connection.createStatement().execute("DROP TABLE IF EXISTS simulation_account");
        executeScript("db/migration/V1__create_simulation_account.sql");
        executeScript("db/migration/V2__create_investment_portfolio.sql");
        executeScript("db/migration/V3__create_instrument_and_position_tables.sql");
    }

    @AfterEach
    void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void v7UpgradesLegacyConstraintPreservesDataAndIsRepeatable() throws Exception {
        connection.createStatement().execute(
                "ALTER TABLE security_instrument DROP CHECK chk_security_instrument_asset_type");
        connection.createStatement().execute("""
                ALTER TABLE security_instrument
                ADD CONSTRAINT chk_security_instrument_asset_type
                CHECK (asset_type IN ('STOCK', 'ETF'))
                """);
        insertInstrument(1, "600519", "STOCK");
        insertInstrument(2, "510300", "ETF");

        executeScript("db/migration/V7__allow_index_instruments.sql");
        executeScript("db/migration/V7__allow_index_instruments.sql");

        insertInstrument(3, "000688", "INDEX");
        assertEquals(3, countRows("security_instrument"));
        assertEquals("Legacy STOCK", instrumentName(1));
        assertEquals("Legacy ETF", instrumentName(2));
        assertThrows(SQLException.class, () -> insertInstrument(4, "INVALID", "BOND"));
    }

    @Test
    void v8AllowsNullFirstBarDerivedFieldsAndPreservesRequiredColumns() throws Exception {
        executeScript("db/migration/V6__add_historical_market_data.sql");
        insertInstrument(1, "600519", "STOCK");
        insertDailyPrice(1, "2026-07-23", "100.0000", "1.0000", "1.010101");
        connection.createStatement().execute("""
                ALTER TABLE instrument_daily_price
                    MODIFY COLUMN previous_close DECIMAL(19,4) NOT NULL,
                    MODIFY COLUMN price_change DECIMAL(19,4) NOT NULL,
                    MODIFY COLUMN percentage_change DECIMAL(19,6) NOT NULL
                """);

        executeScript("db/migration/V8__allow_null_first_daily_price_change.sql");
        executeScript("db/migration/V8__allow_null_first_daily_price_change.sql");

        insertDailyPrice(2, "2026-07-24", null, null, null);
        assertEquals(2, countRows("instrument_daily_price"));
        assertEquals("101.0000", closePrice(1));
        assertEquals("YES", nullableFlag("previous_close"));
        assertEquals("YES", nullableFlag("price_change"));
        assertEquals("YES", nullableFlag("percentage_change"));
        assertEquals("NO", nullableFlag("open_price"));
        assertThrows(SQLException.class, this::insertDailyPriceWithoutOpen);
    }

    @Test
    void currentFreshSchemaSupportsAllTypesAndNullFirstBarDerivedFields() throws Exception {
        executeScript("db/migration/V4__create_risk_config_and_simulated_order_tables.sql");
        executeScript("db/migration/V5__create_trade_and_cash_ledger_tables.sql");
        executeScript("db/migration/V5_1__harden_cash_ledger.sql");
        executeScript("db/migration/V6__add_historical_market_data.sql");
        executeScript("db/migration/V7__allow_index_instruments.sql");
        executeScript("db/migration/V8__allow_null_first_daily_price_change.sql");

        insertInstrument(1, "600519", "STOCK");
        insertInstrument(2, "510300", "ETF");
        insertInstrument(3, "000688", "INDEX");
        insertDailyPrice(1, "2026-07-24", null, null, null);

        assertEquals(3, countRows("security_instrument"));
        assertEquals(1, countRows("instrument_daily_price"));
        assertEquals("YES", nullableFlag("previous_close"));
        assertEquals("NO", nullableFlag("close_price"));
        assertThrows(SQLException.class, () -> insertInstrument(4, "INVALID", "BOND"));
    }

    private void executeScript(String path) throws SQLException {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(path));
    }

    private void insertInstrument(long id, String symbol, String assetType) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO security_instrument
                    (id, symbol, exchange, instrument_name, asset_type, currency, lot_size, status)
                VALUES (?, ?, 'SSE', ?, ?, 'CNY', 1.000000, 'ACTIVE')
                """)) {
            statement.setLong(1, id);
            statement.setString(2, symbol);
            statement.setString(3, "Legacy " + assetType);
            statement.setString(4, assetType);
            statement.executeUpdate();
        }
    }

    private void insertDailyPrice(
            long id,
            String tradeDate,
            String previousClose,
            String priceChange,
            String percentageChange) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO instrument_daily_price
                    (id, instrument_id, trade_date, open_price, high_price, low_price, close_price,
                     previous_close, price_change, percentage_change, volume_lots, amount_cny,
                     adjustment_type, data_source)
                VALUES (?, 1, ?, 100.0000, 102.0000, 99.0000, 101.0000,
                        ?, ?, ?, 1000.0000, 100000.0000, 'NONE', 'TEST')
                """)) {
            statement.setLong(1, id);
            statement.setString(2, tradeDate);
            statement.setString(3, previousClose);
            statement.setString(4, priceChange);
            statement.setString(5, percentageChange);
            statement.executeUpdate();
        }
    }

    private void insertDailyPriceWithoutOpen() throws SQLException {
        connection.createStatement().execute("""
                INSERT INTO instrument_daily_price
                    (id, instrument_id, trade_date, open_price, high_price, low_price, close_price,
                     previous_close, price_change, percentage_change, volume_lots, amount_cny,
                     adjustment_type, data_source)
                VALUES
                    (3, 1, '2026-07-25', NULL, 102.0000, 99.0000, 101.0000,
                     100.0000, 1.0000, 1.000000, 1000.0000, 100000.0000, 'NONE', 'TEST')
                """);
    }

    private int countRows(String tableName) throws SQLException {
        try (var result = connection.createStatement().executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String instrumentName(long id) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT instrument_name FROM security_instrument WHERE id = ?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private String closePrice(long id) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT close_price FROM instrument_daily_price WHERE id = ?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBigDecimal(1).toPlainString();
            }
        }
    }

    private String nullableFlag(String columnName) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'instrument_daily_price'
                  AND column_name = ?
                """)) {
            statement.setString(1, columnName);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }
}
