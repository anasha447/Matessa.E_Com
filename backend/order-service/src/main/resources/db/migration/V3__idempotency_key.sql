-- Client-facing idempotency: a double-clicked checkout must create exactly one order.
-- The unique index is the durable guarantee (survives a cache flush); a partial index lets
-- pre-existing / non-idempotent orders keep a NULL key without colliding.
ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(80);
CREATE UNIQUE INDEX ux_orders_idempotency_key ON orders (idempotency_key) WHERE idempotency_key IS NOT NULL;
