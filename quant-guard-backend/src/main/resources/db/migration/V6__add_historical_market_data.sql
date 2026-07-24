-- QuantGuard migration V6: add historical daily market data.
-- Purpose: stores imported unadjusted daily OHLCV bars and sync results.
-- Data safety: this script does not drop tables, delete data, or overwrite existing rows.
-- Secrets: this script contains no tokens, passwords, or private trading data.

USE quant_guard;

CREATE TABLE IF NOT EXISTS instrument_daily_price (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    instrument_id BIGINT NOT NULL COMMENT 'security_instrument id',
    trade_date DATE NOT NULL COMMENT 'Trading date',
    open_price DECIMAL(19,4) NOT NULL COMMENT 'Open price',
    high_price DECIMAL(19,4) NOT NULL COMMENT 'High price',
    low_price DECIMAL(19,4) NOT NULL COMMENT 'Low price',
    close_price DECIMAL(19,4) NOT NULL COMMENT 'Close price',
    previous_close DECIMAL(19,4) NOT NULL COMMENT 'Previous close price',
    price_change DECIMAL(19,4) NOT NULL COMMENT 'Price change',
    percentage_change DECIMAL(19,6) NOT NULL COMMENT 'Percentage change',
    volume_lots DECIMAL(24,4) NOT NULL COMMENT 'Volume in lots from provider',
    amount_cny DECIMAL(24,4) NOT NULL COMMENT 'Trading amount in CNY yuan',
    adjustment_type VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE means unadjusted daily price',
    data_source VARCHAR(32) NOT NULL COMMENT 'Market data source',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_price_instrument_date_adjustment
        UNIQUE (instrument_id, trade_date, adjustment_type),
    KEY idx_daily_price_instrument_date (instrument_id, trade_date),
    CONSTRAINT fk_daily_price_instrument
        FOREIGN KEY (instrument_id) REFERENCES security_instrument (id),
    CONSTRAINT chk_daily_price_adjustment_type CHECK (adjustment_type IN ('NONE')),
    CONSTRAINT chk_daily_price_positive_prices CHECK (
        open_price > 0 AND high_price > 0 AND low_price > 0 AND close_price > 0
    ),
    CONSTRAINT chk_daily_price_ohlc CHECK (
        high_price >= open_price
        AND high_price >= close_price
        AND high_price >= low_price
        AND low_price <= open_price
        AND low_price <= close_price
    ),
    CONSTRAINT chk_daily_price_volume_amount CHECK (volume_lots >= 0 AND amount_cny >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Unadjusted historical daily prices for imported instruments';

CREATE TABLE IF NOT EXISTS market_data_sync_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    instrument_id BIGINT NOT NULL COMMENT 'security_instrument id',
    provider VARCHAR(32) NOT NULL COMMENT 'Market data provider',
    sync_type VARCHAR(32) NOT NULL COMMENT 'IMPORT, MANUAL, BATCH, or SCHEDULED',
    requested_start_date DATE NOT NULL COMMENT 'Requested start date',
    requested_end_date DATE NOT NULL COMMENT 'Requested end date',
    records_received INT NOT NULL DEFAULT 0 COMMENT 'Rows returned by provider',
    records_inserted INT NOT NULL DEFAULT 0 COMMENT 'Rows inserted locally',
    records_updated INT NOT NULL DEFAULT 0 COMMENT 'Rows updated locally',
    status VARCHAR(32) NOT NULL COMMENT 'RUNNING, SUCCESS, PARTIAL_SUCCESS, or FAILED',
    error_message VARCHAR(512) NULL COMMENT 'Sanitized error message without secrets',
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Sync start time',
    finished_at DATETIME(6) NULL COMMENT 'Sync finish time',
    PRIMARY KEY (id),
    KEY idx_market_data_sync_log_instrument_started (instrument_id, started_at),
    CONSTRAINT fk_market_data_sync_log_instrument
        FOREIGN KEY (instrument_id) REFERENCES security_instrument (id),
    CONSTRAINT chk_market_data_sync_log_status CHECK (
        status IN ('RUNNING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED')
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Market data synchronization result log';
