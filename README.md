# QuantGuard Backend

QuantGuard is a local Spring Boot backend for the 智衡量化交易与风控系统 project.

## Local Database Setup

The backend uses a local MySQL database named `quant_guard`.

1. Create the database by manually executing:

   ```sql
   source src/main/resources/db/create_database.sql;
   ```

   If your MySQL client is not opened from the project root, use the full path to `src/main/resources/db/create_database.sql`.

2. Create the current development tables by manually executing migration scripts in order:

   ```sql
   source src/main/resources/db/migration/V1__create_simulation_account.sql;
   ```

   Flyway is not enabled yet, so these migration files are version-controlled manual SQL scripts for now. The migration script includes `USE quant_guard;`, so it can also be opened and executed directly in MySQL Workbench after the database has been created.

3. Copy the local configuration template:

   ```text
   src/main/resources/application-local.example.yaml
   ```

   to:

   ```text
   src/main/resources/application-local.yaml
   ```

4. Edit `application-local.yaml` with your local MySQL username and password.

   Do not commit `application-local.yaml`. It is ignored by Git because it may contain local credentials.

5. Start the backend and verify:

   ```text
   http://localhost:8080/api/health
   http://localhost:8080/api/database/health
   http://localhost:8080/api/accounts
   ```

## Project Asset Rules

- Durable database scripts belong in `src/main/resources/db/`.
- Real passwords, API keys, tokens, private data, and personal trading data must not be committed.
- Committable local configuration examples should use placeholders only.
