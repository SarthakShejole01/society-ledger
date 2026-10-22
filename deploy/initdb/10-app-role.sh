#!/bin/bash
# Creates the restricted role the application connects as. It does not own the tables, cannot bypass
# row-level security and cannot create anything, so one society can never read another's rows.
# Runs automatically on the first start of the compose database, or by hand on any PostgreSQL:
#   POSTGRES_USER=<owner> POSTGRES_DB=<database> APP_DB_PASSWORD=<password> ./10-app-role.sh
# Safe to run again: it only sets the password and the restrictions.
set -euo pipefail
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD must be set}"
: "${POSTGRES_USER:?POSTGRES_USER must be set}"
: "${POSTGRES_DB:?POSTGRES_DB must be set}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v app_password="$APP_DB_PASSWORD" <<'SQL'
select format('create role ledger_app login password %L', :'app_password')
where not exists (select 1 from pg_roles where rolname = 'ledger_app') \gexec
select format('alter role ledger_app login password %L nosuperuser nocreatedb nocreaterole nobypassrls', :'app_password') \gexec
SQL
