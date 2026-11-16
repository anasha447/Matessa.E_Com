-- Auth hardening (runs once per store schema).

-- 1) Refresh tokens are stored as a SHA-256 hash, never the token itself, and every rotation
--    of one login shares a family_id so a replayed (stolen) token can revoke the whole chain.
alter table refresh_tokens add column family_id  varchar(36);
alter table refresh_tokens add column rotated_at timestamptz;

-- Existing rows hold raw token values. Revoke them (each customer signs in again once) and
-- give each its own family so the column can be made mandatory.
update refresh_tokens set revoked = true, family_id = id::text where family_id is null;
alter table refresh_tokens alter column family_id set not null;
create index idx_refresh_tokens_family on refresh_tokens (family_id);

-- 2) Per-account brute-force lockout (independent of the client's IP).
alter table users add column failed_logins int not null default 0;
alter table users add column locked_until  timestamptz;

-- 3) Emails are case-insensitive identifiers. Lower-case every address that has no
--    case-only twin, then enforce uniqueness on lower(email) when the data allows it.
--    (A store with case-only duplicates keeps working; the twins are reported in the log
--    and must be merged by hand before the index can be created.)
update users u set email = lower(u.email)
where u.email <> lower(u.email)
  and not exists (select 1 from users o where o.id <> u.id and lower(o.email) = lower(u.email));

do $$
begin
    if exists (select lower(email) from users group by lower(email) having count(*) > 1) then
        raise warning 'users: case-only duplicate emails exist; unique index on lower(email) not created';
    else
        create unique index ux_users_email_lower on users (lower(email));
    end if;
end $$;
