#!/bin/bash
# Restores a backup into a database. Objects that already exist in it are replaced.
#   PGHOST=... PGUSER=... PGPASSWORD=... ./restore.sh <dump file> <target database>
# Practise on a scratch database first. The ledger_app role must exist (see initdb/10-app-role.sh),
# because the backup grants it access to the tables.
set -euo pipefail
dump="${1:?usage: restore.sh <dump file> <target database>}"
target="${2:?usage: restore.sh <dump file> <target database>}"

echo "Restoring $dump into '$target'. Existing objects there are replaced." >&2
pg_restore --clean --if-exists --no-owner --dbname="$target" "$dump"
echo "Restored." >&2
