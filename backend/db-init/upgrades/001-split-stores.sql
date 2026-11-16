-- One-off upgrade for databases created BEFORE schema-per-store (everything lived in `public`).
-- Moves the existing data into the `matessa` store schema; the services then create and
-- migrate the other stores' schemas (e.g. `adventure`) themselves on boot via Flyway.
--
-- Not run automatically: this folder is a subdirectory, which the Postgres entrypoint ignores.
-- Fresh installs never need it. Run it once per service database, with the services STOPPED:
--
--   for db in users commercial orders; do
--     docker exec -i matessa-db psql -v ON_ERROR_STOP=1 -U "$USERS_DB_USER" -d "$db" \
--       < backend/db-init/upgrades/001-split-stores.sql
--   done
--
-- Idempotent: does nothing if `matessa` already exists or `public` holds no migrated schema.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'matessa') THEN
        RAISE NOTICE 'schema matessa already exists in %, nothing to do', current_database();
    ELSIF NOT EXISTS (SELECT 1 FROM information_schema.tables
                      WHERE table_schema = 'public' AND table_name = 'flyway_schema_history') THEN
        RAISE NOTICE 'no migrated data in %.public, nothing to move', current_database();
    ELSE
        -- Renaming keeps every table, sequence, index, materialized view and the Flyway history.
        EXECUTE 'ALTER SCHEMA public RENAME TO matessa';
        EXECUTE 'CREATE SCHEMA public';
        EXECUTE format('ALTER SCHEMA matessa OWNER TO %I',
                       (SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = current_database()));
        RAISE NOTICE 'moved %.public -> matessa', current_database();
    END IF;
END
$$;
