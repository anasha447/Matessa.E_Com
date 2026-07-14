-- Queryable shipping state for the admin orders-by-state (India map) analytics.
-- The state was previously reachable only inside the shipping_address_snapshot JSON;
-- promote it to a column set at checkout, backfilled from existing snapshots.

ALTER TABLE orders ADD COLUMN shipping_state varchar(80);

UPDATE orders
SET shipping_state = nullif(btrim(shipping_address_snapshot::jsonb ->> 'state'), '');

CREATE INDEX idx_orders_shipping_state ON orders (shipping_state);
