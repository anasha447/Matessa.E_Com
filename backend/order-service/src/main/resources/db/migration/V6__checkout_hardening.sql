-- Checkout hardening (runs once per store schema).

-- 1) "My orders" lists by user, newest first: one index serves the filter and the sort.
create index if not exists idx_orders_user_created on orders (user_id, created_at desc);

-- 2) The commercial-service reservation behind each order (confirm on OrderPlaced; coupon
--    redemption bookkeeping on cancel).
alter table orders add column reservation_id varchar(36);

-- 3) Idempotency keys as their own records, written BEFORE any work starts:
--    - the same key with a different request body is refused (422), never answered with the
--      order of a different purchase;
--    - a second request while the first is still running is told so (409) instead of racing it;
--    - a failed attempt deletes its row, so retrying with the same key is allowed.
create table idempotency_request (
    idem_key      varchar(80)  primary key,
    requester     varchar(330) not null,               -- u:<userId> | e:<email>
    request_hash  varchar(64)  not null,               -- SHA-256 of the canonical request
    status        varchar(16)  not null,               -- IN_PROGRESS | DONE
    order_id      bigint       references orders (id),
    created_at    timestamptz  not null default now()
);
create index idx_idempotency_request_created on idempotency_request (created_at);

-- 4) Guest coupon use is counted per email.
create index if not exists idx_orders_guest_coupon on orders (lower(guest_email), coupon_code) where guest_email is not null;
