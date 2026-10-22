# Trying it on Render

This is for a **trial**: the `dev` profile logs one-time login codes (visible in Render's logs to anyone with
access to your Render account) and payments use the built-in fake provider. Do not put real members or real
money on it. For real use, see `docs/deployment.md` (real email, Razorpay, a database with backups).

Check Render's current free-plan terms before relying on it. A free web service sleeps when idle (the first
request is slow), and a free PostgreSQL database may expire and has no backups.

## 1. Create the database

Render dashboard, New, PostgreSQL. Name it `society-ledger-db`, pick the free plan and the same region you will
use for the web service. When it is ready, open it and copy:

- **Internal Database URL** host, for example `dpg-abc123-a`, the database name and the user and password.
- **External Database URL**, used once from your computer in step 2.

## 2. Create the restricted role (once)

From your computer (needs the `psql` client; it is included with PostgreSQL installers, or use WSL or Git Bash).
Use the *External* connection details and choose a new password for the app role:

```
export POSTGRES_USER=<db user> POSTGRES_DB=<db name> APP_DB_PASSWORD=<new app password> \
  PGHOST=<external host> PGPASSWORD=<db password> PGSSLMODE=require
bash deploy/initdb/10-app-role.sh
```

On Windows PowerShell set the same variables with `$env:NAME="value"` and run it in Git Bash or WSL instead.

## 3. Deploy the web service

Render dashboard, New, Blueprint, pick this repository and the branch. It reads `render.yaml`. Fill in the
values it asks for:

| Setting | Value |
|---|---|
| `SPRING_DATASOURCE_URL` and `SPRING_FLYWAY_URL` | `jdbc:postgresql://<internal host>:5432/<db name>` |
| `SPRING_DATASOURCE_PASSWORD` | the app password from step 2 |
| `SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD` | the database's own user and password |

`SOCIETY_LEDGER_AUTH_SECRET` is generated for you. The first build takes several minutes.

## 4. Load the sample society (once)

From your computer, with the same *External* settings as step 2:

```
psql -U <db user> -d <db name> -v society_code=sunrise -v society_name="Sunrise Apartments" \
  -f deploy/onboard-society.sql < deploy/flats-template.csv
```

## 5. Use it

Open the service URL Render shows (https://society-ledger.onrender.com or similar). Log in with society code
`sunrise` and `9876500201`. The code is in the service's **Logs** tab, on the line "one-time code for".

## Moving to real use

Remove `SPRING_PROFILES_ACTIVE`, set up real email and the society's Razorpay keys (`docs/deployment.md`), use a
paid database with backups, and test the Content-Security-Policy against the real checkout (`docs/security.md`).
