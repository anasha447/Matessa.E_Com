#!/bin/bash
# Runs once, on the FIRST boot of an empty Postgres data volume (Postgres executes every
# file in /docker-entrypoint-initdb.d/ alphabetically) as the POSTGRES_USER superuser.
#
# Creates one database per service, each OWNED by its own role. The roles are ordinary
# logins (no SUPERUSER / CREATEDB / CREATEROLE): a service owns its database and the schemas
# Flyway creates inside it (one per store), and can touch nothing else on the server.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE ROLE "$USERS_DB_USER" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$USERS_DB_PASSWORD';
	CREATE DATABASE users OWNER "$USERS_DB_USER";

	CREATE ROLE "$COMMERCIAL_DB_USER" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$COMMERCIAL_DB_PASSWORD';
	CREATE DATABASE commercial OWNER "$COMMERCIAL_DB_USER";

	CREATE ROLE "$ORDERS_DB_USER" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$ORDERS_DB_PASSWORD';
	CREATE DATABASE orders OWNER "$ORDERS_DB_USER";

	-- Nobody but the owner connects to a service database.
	REVOKE CONNECT ON DATABASE users, commercial, orders FROM PUBLIC;
EOSQL

echo "init-multi-db: created databases users/commercial/orders, each owned by its own non-superuser role"
