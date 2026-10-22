# Society Ledger

Maintenance collection and tracking for housing societies. Owners see what is due and pay online, everyone sees which flats have paid, and the treasurer handles cash, cheques and exceptions. Built as one product that many societies can use.

Status: early development (version 1 for the first society). See `docs/requirements.md`.

## Layout

- `backend/` Java 21, Spring Boot, PostgreSQL
- `frontend/` Angular web app for phones and desktops
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

## Run the backend locally

```
export SPRING_PROFILES_ACTIVE=dev
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/society_ledger_dev
export SPRING_DATASOURCE_USERNAME=ledger_app
export SPRING_DATASOURCE_PASSWORD=<app role password>
export SPRING_FLYWAY_URL=jdbc:postgresql://localhost:5432/society_ledger_dev
export SPRING_FLYWAY_USER=<schema owner>
export SPRING_FLYWAY_PASSWORD=<schema owner password>
export SOCIETY_LEDGER_AUTH_SECRET=<a long random string>
cd backend && ./gradlew bootRun
```

The `dev` profile logs one-time codes instead of emailing them. Never enable it for real members.
Create the `ledger_app` role (login, no table ownership) before the first start.

## Run the frontend

```
cd frontend
npm install --legacy-peer-deps
npx ng serve        # http://localhost:4200, forwards /api to the backend on port 8080
npx ng test --watch=false
```

Angular 21 (standalone components, signals), tests on Vitest. `--legacy-peer-deps` works around an npm
install bug seen with npm 10.

## API (version 1)

| Method and path | Who |
|---|---|
| `POST /api/auth/otp` | anyone, always answers 202 |
| `POST /api/auth/verify` | anyone, returns a session token |
| `GET /api/flats/{flatId}/dues?month=2026-10` | an owner for their own flat; treasurer and committee for any flat |
| `GET /api/flats/status?month=2026-10` | every member, status only |
| `POST /api/payments` | owner or treasurer, for any flat; the server sets the amount |
| `POST /api/payments/offline` | treasurer only: record cash or cheque, optionally waiving the late fee with a reason |
| `GET /api/dashboard?month=2026-10` | treasurer and committee: counts, expected and collected, online against offline, late fees, pending flats |

All paths except `/api/auth/**` need `Authorization: Bearer <token>`. Errors are RFC 7807 problems.
