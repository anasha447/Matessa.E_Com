-- Guest checkout: orders may be placed without an account. user_id becomes optional,
-- and a guest_email is captured instead so the buyer can track the order via /track.
ALTER TABLE orders ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE orders ADD COLUMN guest_email VARCHAR(255);
