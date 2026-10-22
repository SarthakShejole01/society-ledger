# Deployment

## What is deployed

One container image (the `Dockerfile`) holds the backend and the built web app, so there is one service to run and no cross-origin setup. It talks to one PostgreSQL database. Everything else (email, the payment gateway when it exists) is reached over the network.

## Settings

All settings are environment variables. None are committed. `.env.example` lists them.

| Setting | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | The database, as the restricted role `ledger_app` |
| `SPRING_FLYWAY_URL`, `_USER`, `_PASSWORD` | The same database as the table owner, used only to run migrations at start |
| `SOCIETY_LEDGER_AUTH_SECRET` | At least 16 random characters. Protects login codes and session tokens. Changing it logs everyone out |
| `SOCIETY_LEDGER_MAIL_ENABLED`, `SOCIETY_LEDGER_MAIL_FROM`, `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` | Real email for login codes |
| `PORT` | Port to listen on (default 8080). Hosting platforms set it |
| `SPRING_PROFILES_ACTIVE` | Leave empty in production. `dev` logs login codes and must never be used for real members |

The app refuses to start without a way to deliver login codes: either real email is enabled or the `dev` profile is on.

## First deploy

1. **Create a managed PostgreSQL 16 database** with automatic daily backups. Do not use a database that sleeps, pauses, or has no backups.
2. **Create the restricted role.** Run `deploy/initdb/10-app-role.sh` with `POSTGRES_USER`, `POSTGRES_DB` and `APP_DB_PASSWORD` set and the usual `PG*` connection settings. It is safe to run again.
3. **Run the image** with the settings above. On start it applies the database migrations as the table owner, then switches to the restricted role for everything else.
4. **Check** `/actuator/health` answers `UP`, and that the host serves the app over HTTPS.
5. **Load the society** (below), then log in as the treasurer and look at the dashboard.

To try everything on one machine: `cp .env.example .env`, edit it, `docker compose up --build`.

## Loading a society

There is no admin screen yet. The treasurer's spreadsheet is loaded with one script:

```
psql "<owner connection>" -v society_code=sunrise -v society_name="Sunrise Apartments" \
  -f deploy/onboard-society.sql < flats.csv
```

`deploy/flats-template.csv` shows the columns: flat number, floor, monthly amount in rupees, and for each person a name, mobile, email and role (`OWNER`, `TREASURER` or `COMMITTEE`). Run it as the table owner, not as `ledger_app`. It is all-or-nothing: a bad mobile number, a missing email, a repeated flat or a repeated mobile number stops it with a plain message and loads nothing. Every person who should log in needs an email address, because that is where their code goes.

## Backups and restore

```
BACKUP_DIR=/backups PGHOST=... PGUSER=... PGPASSWORD=... PGDATABASE=... deploy/backup.sh
PGHOST=... PGUSER=... PGPASSWORD=... deploy/restore.sh <dump file> <target database>
```

`backup.sh` writes a compressed dump, checks it can be read back, and keeps the newest 14 (`BACKUP_KEEP`). Schedule it daily, and copy the dumps off the machine, because a backup next to the database does not survive losing it. Practise a restore into a scratch database before you need one, and check the counts. The managed database's own backups are a second layer, not a replacement.

## Upgrades and rollback

Deploy the new image. Migrations run at start and only ever move forward. If an upgrade goes wrong, redeploy the previous image if the schema did not change, otherwise restore the last backup.

## What was checked, and what was not

Checked by running it:

- The packaged jar serves the web app, direct links to every screen, the API, health, gzip for larger files and a graceful shutdown. A real browser logged in and refreshed on a deep link.
- The role script on an existing server and on a fresh one.
- Backup, keeping only the newest, restoring into a scratch database, matching row counts for every table, tenant isolation and audit protection surviving the restore, and a broken dump being detected.
- The society loader with a good sheet, a bad mobile number, a repeated society, a repeated flat and a repeated mobile.
- Frontend install, tests and production build from a clean copy using the committed lockfile.

Not checked, because this machine has no Docker and no access to GitHub Actions:

- The Docker image build and `docker compose up`.
- The CI workflow itself, including the database tests running on Testcontainers. Watch its first run closely.
- Real email through a real provider.
- HTTPS, the host's health checks and any real hosting platform.

## Known gaps before real money moves

- **The payment gateway is not connected.** Owners can start a payment but it never completes, so nothing is charged and nothing turns Paid online. Starting a payment leaves a pending record that never expires.
- **A member has one role.** A committee member who also owns a flat can see everything but cannot pay their own maintenance online, because the committee role is read-only. Decide whether committee members with a flat may pay for it.
- **No admin console.** Societies and people are loaded and changed with SQL.
- **Security review still to do.** No security headers or content policy are set, only the login code request is rate limited, and there is no account lockout beyond the code limits.
- **Hosting.** Free tiers that sleep or have no backups are fine for trying it, not for real payments. Check each platform's current terms before choosing.
