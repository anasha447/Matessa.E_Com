# Audit follow-up — store isolation, security, scale-out (Nov 2026)

> Living tracker for the full-stack audit of branch `feat/store-isolation`. Read this before
> touching auth, checkout, tenancy, deployment or the BFFs. The narrative report with
> diagrams is `docs/audit-report.html`; the design itself is in `ARCHITECTURE.md` §8.1–§8.2.
> Status: **fixed** (merged on this branch, with tests where noted), **deferred** (reason
> given), **open** (needs a decision from the owner).

## 1. Store isolation (two storefronts, one backend)

| # | Finding | Status | Where |
|---|---|---|---|
| I1 | Missing store context silently fell back to `matessa` (DB and cache) | fixed — throws once the app is ready | `tenant/TenancyConfig`, `config/CacheConfig` |
| I2 | Public gateway let callers pick any store; token store never compared to header | fixed — `StoreGuardFilter` (400/403); gateway no longer published in prod | `api-gateway/tenant`, `docker-compose.prod.yml` |
| I3 | Kafka record without a store was dropped with its offset committed | fixed — non-retryable → `<topic>.DLT` (order-service got a DLT handler too) | `tenant/KafkaTenancy`, `KafkaErrorHandlerConfig` |
| I4 | `STORE_ID` overridable by `NEXT_PUBLIC_TENANT_ID` | fixed — constant in code | `lib/api/config.ts` (both apps) |
| I5 | Store ids `public` / `pg_catalog` accepted | fixed — refused at boot | `TenantProperties` (×3), gateway |
| I6 | Second storefront loaded images straight from the gateway | fixed — same-origin `/media` rewrite | `second-frontend/lib/utils/media.ts` |
| I7 | Both stores shared one image folder; image URL free text | fixed — keys `<store>/<id>.webp`; URL must be own store, legacy `/media/products/…`, or `https://` | `storage/*`, `ProductImageServiceImpl` |
| I8 | Only user-service had a cross-store test | fixed — commercial `StoreIsolationIntegrationTest`, gateway tests, consumer DLT path | tests |
| I9 | Rate-limit buckets shared across stores | fixed — store in the key | `RateLimitFilter` |
| I10 | One DB role per service owns every store schema (SQL injection would reach both stores) | deferred — by design of schema-per-store; separate roles per store is a later step | ARCHITECTURE §8.1 |
| I11 | Second storefront shows stub products when the backend is down/empty | open — part of `adventure.refactor.md` | `second-frontend/lib/server/catalog.ts`, `lib/stubs` |

## 2. Security

| # | Sev | Finding | Status |
|---|---|---|---|
| H1 | High | Mock payment marked every card/online order PAID in production | fixed — `@Profile("!prod")` mock; prod = `CodOnlyPaymentService`; checkout shows only `GET /api/orders/payment-methods` |
| H2 | High | Browser `X-Forwarded-For` forwarded → login rate limit bypass | fixed — BFF forwards only Caddy's `X-Real-IP` (`TRUST_PROXY_IP_HEADER`); gateway trusts only the compose subnet; per-account lockout (5 fails → 15 min) |
| H3 | High | BFF forwarded any path (raw refresh tokens via `/api/auth/*`, `/actuator`) | fixed — allow-list + `..`/encoded-slash refusal (`lib/server/guard.ts`) |
| M1 | Med | Refresh tokens stored in plain text, no reuse detection | fixed — SHA-256 at rest, token families, reuse revokes family (10 s grace) |
| M2 | Med | Enumeration / timing on login and register | fixed for login (dummy BCrypt, same answer); register still says "exists" — inherent without email verification (see O3) |
| M3 | Med | No CSRF/Origin check on session + BFF writes | fixed — `Sec-Fetch-Site`/Origin + JSON/multipart only |
| M4 | Med | Open redirect via `?next=` | fixed — `safeNext()` |
| M5 | Med | No caps on quantities / lines; stock held by fake orders | fixed — ≤20 per line, ≤50 lines; checkout rate limit; abandoned reservations expire |
| M6 | Med | Guest coupon per-user limit bypass; unlimited welcome coupons | fixed for per-user limit (counted per email, under a coupon row lock); welcome-coupon minting per unverified email remains — see O3 |
| M7 | Med | No security headers | fixed — CSP, frame-ancestors, nosniff, referrer, permissions, HSTS (prod) |
| M8 | Med | user-service connected as the Postgres superuser | fixed for new volumes (separate superuser, non-superuser owners); existing volumes: `backend/db-init/upgrades/002-least-privilege.sql` |
| M9 | Med | Forced password change enforced only in the UI | fixed — token carries no roles until changed; UI re-signs-in |
| M10 | Med | No image pixel limit | fixed — 4096 px/side checked from the header before decode. Bundled libwebp version in `webp-imageio 0.2.2` not verified — see O4 |
| L1 | Low | `/internal/**` open on the network | fixed — `X-Internal-Key` (`INTERNAL_API_KEY`) |
| L2 | Low | Guessable order numbers | fixed — `MA261116-7K3QXD` (store prefix + day + 30 random bits) |
| L3 | Low | Case-sensitive email | fixed — lower-cased on write/lookup; unique index on `lower(email)` where data allows |
| L4 | Low | Services accept an `access_token` cookie | deferred — harmless now (browsers never reach services); remove with the next filter change |
| L5 | Low | Staff input gaps | fixed: `priceOverride ≥ 0`, percentage ≤ 100. Deferred: verified-purchase reviews (needs an order-service lookup, see O5) |
| L6 | Low | Guest contact/newsletter got 401 at the gateway | fixed |
| L7 | Low | JWT has no `aud`; role/ban changes take ≤15 min | accepted |

## 3. Design & scale-out

| # | Finding | Status |
|---|---|---|
| D1 | Stock/coupon stayed held on commit failure or reserve timeout; nothing expired reservations | fixed — reservation ids, idempotent reserve/release, `ReservationExpiryJob`, release on every failure path |
| D2 | Checkout held a DB connection across remote reserve + payment | fixed — orchestrated outside a transaction; one short persist transaction |
| D3 | Outbox pollers published the same rows on every replica | fixed — `FOR UPDATE SKIP LOCKED`, batched sends |
| D4 | Scheduled jobs ran on every replica | fixed — ShedLock (analytics refresh, reservation expiry, idempotency cleanup) |
| D5 | Images on local disk (one replica only) | fixed — `ObjectStore` (S3/MinIO in prod, local in dev) |
| D6 | Rate limit INCR/EXPIRE not atomic | fixed — Lua script |
| D7 | Cache: KEYS on clear, eviction before commit, Redis outage = 500 | fixed — SCAN batches, transaction-aware, fail-open |
| D8 | No breaker on catalog routes | fixed — `commercialService` breaker + 15 s limit |
| D9 | Orders list unindexed for its sort | fixed — `(user_id, created_at desc)` |
| D10 | Default pool everywhere | fixed — `DB_POOL_SIZE`, 5 s connection timeout |
| D11 | Trace sampling 0 or 100 % | fixed — env, default 0.1 (needs a collector; prod compose keeps 0) |
| D12 | Cart pricing = one Feign call per line | deferred — fine at current basket sizes (≤50 lines); batch endpoint is a later optimisation |
| D13 | Discount and review hard deletes | deferred — not referenced by other services; convert to soft delete if that changes |

## 4. Checkout page (both storefronts)

Done: one idempotency key minted on page load and locked once a request has gone out; one request in flight; 20 s timeout with "check your orders"; server-driven payment methods; `noindex` + no-store; Matessa also re-validates the basket on arrival (stock, limits, price changes), validates Indian mobile/PIN inline, keeps the form across reloads (not a guest's email), and replaces history with `/order-confirmed` on success.

## 5. Open decisions for the owner

- **O1 Payment provider.** Production takes COD only. Pick a provider (Razorpay fits India); the integration must mark orders PAID only from a signature-verified webhook.
- **O2 Health claims.** "Why Drink Mate?" copy says mate may "help protect against heart disease" — a medical claim; have it reviewed (FSSAI advertising rules).
- **O3 Email verification.** Without it, sign-up reveals existing accounts and the newsletter mints a welcome coupon per unverified address. Adding a verification email closes both.
- **O4 libwebp.** Confirm the native libwebp bundled in `webp-imageio 0.2.2` is ≥ 1.3.2 (CVE-2023-4863) or switch to a maintained encoder.
- **O5 Verified-purchase reviews.** Needs an internal "has user X bought product Y" call to order-service.
- **O6 npm audit.** `npm audit --omit=dev` findings are listed in the report; nothing was force-upgraded.
- **O7 Pre-existing lint errors.** `frontend` lint still reports errors in `Reveal.tsx` and `StackGallery.tsx` (not touched by this audit); builds pass.
- **O8 First call after a restart.** Spring/Feign warm-up makes the first catalog call after an order-service restart slow; under heavy CPU load (tests compiling on the same machine) it exceeded the 3 s read limit once. On the VPS keep restarts rolling, or add a warm-up call on startup if it shows up in logs.

## 4b. Live end-to-end run (17 Nov 2026)

Full stack in Docker (`docker compose -p matessa-verify … -f docker-compose.yml -f docker-compose.dev.yml`) + both storefronts in dev, driven through the BFFs like a browser: **59/59 checks pass** — admin/customer auth, product-level stock, cart, registered + guest COD checkout, idempotent replay and 422, tracking, oversell refusal, DTO limits, role checks, BFF allow-list, cross-site refusal, security headers, cross-store invisibility of products/accounts/cookies/tokens, gateway store rules, account lock. Reservations of placed orders all reached CONFIRMED via Kafka. The run found and fixed one bug (registered retry after success → "Cart is empty") and a too-tight reserve timeout — commit `82878a9`.

## 6. How to run the tests (this Windows machine)

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-25"          # Maven reads JAVA_HOME, not PATH
export TESTCONTAINERS_RYUK_DISABLED=true                  # Ryuk unreachable on this Docker Desktop
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv6Addresses=true -Dtest.redis.host=::1 -Dtest.minio.host=[::1]"
docker run -d --name mt-test-redis -p 56379:6379 redis:7-alpine   # commercial-service cache tests
export REDIS_PORT=56379 REDIS_HOST=::1
cd backend/<service> && ./mvnw test
```

Docker Desktop's IPv4 port forwarding on this machine accepts and then drops connections; IPv6 `::1` works. On a normal Linux CI runner none of the exports except `JAVA_HOME` are needed.

## 7. Deploying on the VPS

1. `cp env.example .env`, fill every `CHANGE_ME`, set `MATESSA_DOMAIN`, `ADVENTURE_DOMAIN`, `ACME_EMAIL`, `MINIO_ROOT_*`, `INTERNAL_API_KEY`, `PG_SUPERUSER_PASSWORD`.
2. Point both domains' DNS at the VPS. Generate the RS256 keys into `keys/`.
3. `docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile prod up -d --build`
4. Only ports 80/443 are open. Existing database volume from before this change? Run `backend/db-init/upgrades/001-split-stores.sql` (stores) and `002-least-privilege.sql` (roles) once.
5. Existing product images: copy the old volume's `products/` folder into the MinIO bucket `matessa-media` under `products/` (their URLs stay valid).

## 8. Next

- `adventure.refactor.md` (second storefront) — to be supplied; stub-product removal (I11) belongs there.
