#!/bin/bash
# Writes a compressed backup of the database and keeps the newest BACKUP_KEEP (default 14).
# Uses the standard PGHOST, PGPORT, PGUSER, PGPASSWORD and PGDATABASE settings.
#   BACKUP_DIR=/backups PGHOST=... PGUSER=... PGPASSWORD=... PGDATABASE=... ./backup.sh
# Copy the result somewhere else too: a backup on the same machine does not survive losing the machine.
set -euo pipefail
: "${BACKUP_DIR:?BACKUP_DIR must be set}"
keep="${BACKUP_KEEP:-14}"

mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/society-ledger-$(date -u +%Y%m%dT%H%M%SZ).dump"
pg_dump --format=custom --no-owner --file="$file.partial"
mv "$file.partial" "$file"
# A dump that cannot be read back is worth nothing, so check it now.
pg_restore --list "$file" > /dev/null

ls -1t "$BACKUP_DIR"/society-ledger-*.dump | tail -n +"$((keep + 1))" | xargs -r rm --
echo "$file"
