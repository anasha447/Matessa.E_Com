#!/bin/bash
# Runs once, on the FIRST boot of an empty Postgres data volume (Postgres executes every
# file in /docker-entrypoint-initdb.d/ alphabetically). The container's POSTGRES_* env
# already created the `users` database owned by the bootstrap superuser; here we add the
# other two databases, each with its OWN role — so one Postgres process still keeps the
# database-per-service boundary (separate DBs, separate credentials, no cross-DB joins).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE ROLE "$COMMERCIAL_DB_USER" WITH LOGIN PASSWORD '$COMMERCIAL_DB_PASSWORD';
	CREATE DATABASE commercial OWNER "$COMMERCIAL_DB_USER";

	CREATE ROLE "$ORDERS_DB_USER" WITH LOGIN PASSWORD '$ORDERS_DB_PASSWORD';
	CREATE DATABASE orders OWNER "$ORDERS_DB_USER";
EOSQL

echo "init-multi-db: created databases 'commercial' (owner $COMMERCIAL_DB_USER) and 'orders' (owner $ORDERS_DB_USER)"
