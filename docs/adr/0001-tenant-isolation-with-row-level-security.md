# 0001. Tenant isolation with PostgreSQL row-level security

## Context

One deployment serves many societies. A bug that returns another society's flats or payments is the worst failure this product can have.

## Options

1. **Filter by `society_id` in every query.** Simple, but one forgotten `where` leaks data.
2. **One database or schema per society.** Strong isolation, but costly to run and migrate for many small societies.
3. **Shared tables with `society_id` plus row-level security (chosen).** The database refuses to return other societies' rows, whatever the application does.

## Decision

- Every tenant table has `society_id` and a row-level security policy that compares it with `app.society_id`, a setting the application sets for the duration of one transaction (`SocietyScope`).
- No society set means no rows are visible. The system fails closed.
- The application connects as `ledger_app`, a role that does not own the tables, so the policies always apply. Migrations run as the schema owner.
- Platform-wide work (onboarding, billing) will use a separate, narrowly scoped role and is logged.

## Consequences

- Repository code does not need a `society_id` filter, but it must run inside `SocietyScope`.
- Tests need a real PostgreSQL, and they must connect as the application role to prove isolation.
- Production needs the `ledger_app` role created before the first migration.
