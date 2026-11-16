-- One-off upgrade for a database volume created BEFORE the least-privilege split, where the
-- users service connected as the server's bootstrap superuser (USERS_DB_USER).
--
-- Run ONCE, connected as that old superuser, with the stack's services stopped:
--   docker compose exec -T matessa-db psql -U "$USERS_DB_USER" -d users \
--     -v admin_password="'<new superuser password>'" -f - < backend/db-init/upgrades/002-least-privilege.sql
-- then put PG_SUPERUSER=postgres_admin and PG_SUPERUSER_PASSWORD=<that password> in .env.
--
-- After this, users_app (or whatever USERS_DB_USER is) is an ordinary owner of `users`.

CREATE ROLE postgres_admin WITH LOGIN SUPERUSER PASSWORD :admin_password;

-- Demote the old bootstrap user. It keeps ownership of the users DB and its schemas.
DO $$
BEGIN
    EXECUTE format('ALTER ROLE %I NOSUPERUSER NOCREATEDB NOCREATEROLE', current_user);
END $$;

REVOKE CONNECT ON DATABASE users, commercial, orders FROM PUBLIC;
