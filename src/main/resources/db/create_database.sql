-- QuantGuard database creation script.
-- Purpose: create the base database for the local QuantGuard backend.
-- Scope: only creates the quant_guard database; it does not create business tables.
-- Repeatable: safe to run multiple times because CREATE DATABASE IF NOT EXISTS is used.
-- Data safety: this script does not delete or overwrite any data.
-- Secrets: this script does not contain database passwords, API keys, or private data.
-- Usage: execute manually in MySQL before starting the backend with database access enabled.

CREATE DATABASE IF NOT EXISTS quant_guard
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
