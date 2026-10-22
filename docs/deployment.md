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
| `SOCIETY_LEDGER_GATEWAY_PROVIDER` | `razorpay` in production. Empty with the `dev` profile uses a built-in fake provider |
| `SOCIETY_LEDGER_GATEWAY_RAZORPAY_KEY_ID`, `_KEY_SECRET`, `_WEBHOOK_SECRET` | The society's own Razorpay keys and webhook secret |
| `PORT` | Port to listen on (default 8080). Hosting platforms set it |
| `SPRING_PROFILES_ACTIVE` | Leave empty in production. `dev` logs login codes and must never be used for real members |

The app refuses to start without a way to deliver login codes (real email enabled, or the `dev` profile) and without a payment provider (`razorpay` configured, or the `dev` profile). That is deliberate: it never runs quietly without them.

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

## Online payments (Razorpay)

Each society should use its own Razorpay account, so the money settles straight into that society's bank account and never passes through yours. Check with a compliance adviser before pooling several societies' payments through one account.

1. The society opens a Razorpay account and completes its KYC. Use **test mode** keys first.
2. Set the three `SOCIETY_LEDGER_GATEWAY_RAZORPAY_*` values and `SOCIETY_LEDGER_GATEWAY_PROVIDER=razorpay`.
3. In Razorpay's dashboard, add a webhook pointing at `https://<your host>/api/gateway/webhook`, subscribe it to `payment.captured` (and `order.paid`), and give it a secret. Put that secret in `SOCIETY_LEDGER_GATEWAY_RAZORPAY_WEBHOOK_SECRET`.
4. In test mode, pay a small month for a test flat with a test method and watch for **Paid** on the board. Try closing the payment page before paying: nothing should be charged and the flat should stay Pending.
5. Switch to live keys and a live webhook, then do one real small payment before telling residents.

How it works: starting a payment creates a Razorpay order for the amount the server worked out, in paise. The owner pays on Razorpay's page. A flat turns Paid only when the server receives a payment signed by Razorpay for that exact order, either from the browser or from the webhook, whichever arrives first. A repeat of either is harmless. The amount is never taken from the browser.

Razorpay's fee is deducted from what the society receives (the society bears it). The dashboard shows online and offline money separately so it can be matched to Razorpay's settlement report and the bank statement.

If the browser closes after paying, the webhook still marks the flat Paid. If the webhook is rejected for a wrong amount, the payment stays Pending and an error is logged for the treasurer to look at.

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

Checked by the first CI run on GitHub (all three jobs passed on the commit that added the workflow):

- The backend tests, including the database tests, which there start a real PostgreSQL with Testcontainers.
- The frontend install from the lockfile, the tests and the production build.
- The Dockerfile builds the image. It was built, not run.

Not checked:

- Running the container, and `docker compose up`. CI only builds the image.
- Real email through a real provider, and a real Razorpay account in test or live mode.
- HTTPS, the host's health checks and any real hosting platform.

## Known gaps before real money moves

- **Razorpay is built but has not been tried against Razorpay.** It was tested against a local stand-in for its server and its documented signature rules, and end to end with a built-in fake provider. Do the test-mode steps above before real money moves.
- **Abandoned payments never expire.** Each attempt leaves a pending record, so someone who starts and walks away leaves clutter.
- **A committee member who owns a flat can pay only their own flat** (not others), and cannot record payments or waive fees. Change this if the committee wants otherwise.
- **No admin console.** Societies and people are loaded and changed with SQL.
- **Security.** Browser security headers are set, but no Content-Security-Policy yet (it must be tested with the real Razorpay checkout), and only the login code request is rate limited. See `docs/security.md` for the full list.
- **Hosting.** Free tiers that sleep or have no backups are fine for trying it, not for real payments. Check each platform's current terms before choosing.
