-- Adventure Dose user-service — force-change-on-first-login flag (users DB)
-- Set true when an admin provisions a staff account with a temporary password;
-- cleared when the user sets their own password via self-service.

alter table users
    add column must_change_password boolean not null default false;
