# Society Ledger

Maintenance collection and tracking for housing societies. Owners see what is due and pay online, everyone sees which flats have paid, and the treasurer handles cash, cheques and exceptions. Built as one product that many societies can use.

Status: early development (version 1 for the first society). See `docs/requirements.md`.

## Layout

- `backend/` Java 21, Spring Boot, PostgreSQL
- `docs/` requirements and decisions

## Run the backend tests

```
cd backend
./gradlew test
```

Database tests use a real PostgreSQL. With Docker available they start one with Testcontainers.
Without Docker, point them at a local database:

```
export SOCIETY_LEDGER_TEST_DB_URL=jdbc:postgresql://localhost:5432/society_ledger_test
export SOCIETY_LEDGER_TEST_DB_USER=postgres
export SOCIETY_LEDGER_TEST_DB_PASSWORD=postgres
./gradlew test
```

The application connects as the `ledger_app` role, which does not own the tables, so tenant
isolation (row-level security) always applies. See `docs/adr/0001-tenant-isolation-with-row-level-security.md`.
