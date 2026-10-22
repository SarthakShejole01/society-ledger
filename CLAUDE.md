# Working agreement for AI coding agents

This file tells AI assistants how to work in this repository. Read `docs/requirements.md` before making changes.

## How we work

1. **Test first.** Write a failing test, make it pass with the simplest code, then refactor. No production code without a test that needs it.
2. **Small steps.** One behaviour per change, committable on its own with all tests green.
3. **Stay in scope.** Build only what `docs/requirements.md` lists. If something seems missing, ask.
4. **Explain trade-offs.** When there is more than one reasonable approach, state the options and the choice before coding. Significant decisions get a short note in `docs/adr/`.

## Backend conventions (Java 21, Spring Boot)

- Package by feature: `common`, `billing`, `society`, `payment`, `auth`.
- **Money is whole paise in the `Money` value object. Never `double`, `float` or `BigDecimal` for amounts.**
- **Every tenant table carries `society_id`.** A query that can return another society's rows is a bug; test for it.
- Controllers are thin; business rules live in services; entities never leave the service layer (use DTOs/records).
- Constructor injection only.
- Validate input with Bean Validation on request DTOs; errors return RFC 7807 `ProblemDetail`.
- Schema changes only through new Flyway migrations. Never edit an applied migration.
- Inject `java.time.Clock`; never call `LocalDate.now()` or `Instant.now()` in domain code.
- Dates that decide money (late fee cutoff) use `Asia/Kolkata`.
- The server is the only source of truth for amounts. Never trust an amount sent by a client.
- Payment gateway callbacks must be safe to receive twice (unique gateway reference).

## Tests

- Fast and deterministic: no real clock, no unseeded randomness, no sleeps, no network.
- Name tests by behaviour: `late_fee_applies_from_midnight_of_the_eleventh_in_india`.
- Arrange / Act / Assert, one behaviour per test, AssertJ for assertions.
- Unit tests for domain logic; `@WebMvcTest` for HTTP contracts; real PostgreSQL (Testcontainers) for SQL and tenant-isolation tests. No H2.

## Commits

Conventional style, small and frequent: `test: …`, `feat: …`, `refactor: …`, `fix: …`, `docs: …`, `chore: …`, `ci: …`.

## Don'ts

- Don't disable, skip or delete failing tests to make the build pass.
- Don't add dependencies without saying why.
- Don't commit secrets; configuration comes from environment variables.
- Don't store or log full phone numbers, UPI IDs or card data beyond what the feature needs.
