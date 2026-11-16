# Adventure Dose — Architecture Reference

> **What this is.** A reference for AI agents building the Adventure Dose backend (Spring Boot) and frontend (Next.js). It documents the *decisions, patterns, and conventions* to follow — not a step-by-step script. Consult the relevant section when building or reviewing code. When code conflicts with this doc, this doc wins unless the human says otherwise. Pairs with `DATA-MODEL.md` (entities) — this doc covers everything around the data: services, infra, security, API style.
>
> **Phase tags.** `[v1]` = build now. `[later]` = target architecture, add when load or features demand it; do not block v1 on these. Building all `[later]` items upfront slows delivery and learning.

---

## 1. Stack (the recommended technologies)

| Layer | Choice | Phase |
|---|---|---|
| Backend services | Java + Spring Boot (3 services + Eureka + Gateway) | v1 |
| Service discovery | Eureka (Spring Cloud Netflix) | v1 |
| API gateway | Spring Cloud Gateway | v1 |
| Sync inter-service calls | OpenFeign + Spring Cloud LoadBalancer | v1 |
| Async messaging | Kafka | v1 (events for order→stock) |
| Database | PostgreSQL, one per service | v1 |
| ORM | Spring Data JPA / Hibernate | v1 |
| Auth | Stateless JWT, RS256 | v1 |
| Frontend | Next.js (App Router) + Tailwind | v1 |
| Resilience | Resilience4j (timeouts, circuit breaker) | v1 for Feign calls |
| Caching | Redis | later |
| Static delivery | CDN | later |
| Server push | WebSocket | later |
| Reliable events | Transactional Outbox + idempotent consumer | later (v1 may publish directly) |

**Default posture:** REST everywhere, stateless services, config via environment variables, Docker for everything.

---

## 2. System shape & request flow

```
Next.js client ──> Load balancer ──> API Gateway ──> Service ──> its own Postgres
                        (later)         (v1)           (v1)
CDN (later) serves static assets directly to the client.
Kafka (v1) carries async side-effects between services.
Eureka (v1) lets the gateway and services find each other by name.
```

- The gateway is the single public entry point. In v1 it can sit directly behind the client; a load balancer in front of multiple gateway instances is `[later]`.
- Synchronous path is left-to-right (browse, checkout). Kafka handles fire-and-forget side-effects (stock decrement, history, future email).
- Three services: **user-service**, **commercial-service**, **order-service** — see `DATA-MODEL.md` for ownership.

---

## 3. Build order (reference, not a mandate)

The dependency-correct sequence, so each piece has what it needs to run. The agent does not have to follow this rigidly, but earlier items unblock later ones:

1. **Eureka server** — nothing registers without it.
2. **user-service** — register/login, issue JWT. Unblocks all auth.
3. **API gateway** — routing + JWT validation; login now flows through it.
4. **commercial-service** — catalog/variants, manager-only writes.
5. **order-service** — cart + checkout; first Feign call (price) + first Kafka event (`OrderPlaced`).
6. **Async wiring** — commercial-service consumes `OrderPlaced`, decrements stock.
7. **Cross-cutting** — global exception handling, validation, Swagger, Dockerize, then Next.js integration.

Principle: get one service fully working end-to-end (DB → secured API → Postman) before starting the next. Avoid three half-finished services.

---

## 4. Scalability & statelessness `[principle, v1]`

- **Scale out, not up.** The target is many identical instances behind a balancer (horizontal), not one bigger box (vertical). v1 may run single instances, but nothing in the code may *assume* a single instance.
- **Services must be stateless.** No per-user session state in service memory. Identity comes from the JWT on each request; the cart lives in `orders` DB, not memory. This is the property that makes running N copies trivial — do not break it.
- **Hard rule:** never store request/session/user state in a service instance's memory. If something feels like it needs to be remembered between requests, it goes in Postgres or (later) Redis.

---

## 5. Load balancing `[v1 east-west, later edge]`

- **Client-side (v1):** inter-service calls resolve instances via Eureka + Spring Cloud LoadBalancer. order-service calling commercial-service picks an instance itself; no central proxy for internal traffic.
- **Edge (later):** a load balancer in front of gateway instances for public traffic; round-robin or least-connections.
- **Health checks matter:** expose `/actuator/health`; a balancer must stop routing to an instance that fails its probe. Avoid sticky sessions — they fight statelessness.

---

## 6. Caching with Redis `[later]`

Not in v1. When read load justifies it:

- **Cache the read-heavy, change-rarely data:** product catalog reads, category trees, homepage lists, hot product detail by variant id. Also good for rate-limit counters.
- **Do not cache:** per-user/authed data, live prices that must be exact at checkout, order data.
- **Two hard parts to handle explicitly:**
  - *Invalidation* — on a product write (manager edit), evict or use a short TTL, or the cache serves stale data. Easiest reliable approach: short TTL + evict-on-write.
  - *Stampede* — when a hot key expires, many requests hit Postgres at once. Mitigate with a lock or staggered TTLs.

---

## 7. CDN `[later]`

- **On the CDN:** product/artwork images, Next.js JS/CSS bundles, fonts, icons, logo — anything static and public (if two users get identical bytes, it can be on the CDN).
- **Stays on origin:** cart/checkout APIs, anything authed or per-user, live prices, order data.
- Benefit: lower global latency, origin offload, spike resilience.

---

## 8. PostgreSQL & data rules `[v1]`

- **One database per service**, no shared schema, no cross-database joins or FKs. (Full entity detail in `DATA-MODEL.md`.)
- Use Postgres for ACID correctness on money and stock.
- **Optimistic locking** (`@Version` on `ProductVariant`) to prevent overselling — two concurrent checkouts on the last item: one wins, the other gets a 409.
- **Money is `BigDecimal`**, never `double`/`float`.
- **All `@OneToMany` are `FetchType.LAZY`** to avoid N+1 query blowups.
- **Soft deletes** (`active = false`) on anything another service references (products, variants, users) — never hard-delete, so historical orders never dangle.
- JSONB columns are fine for snapshots (e.g. shipping address on an order).

### 8.1 Stores: one schema per store `[v1]`

One backend serves several independent stores (today `matessa` and `adventure`, listed in
`APP_TENANCY_TENANTS`). Each store is its **own Postgres schema inside every service's
database** — its own products, customers, admins, carts, orders, coupons and analytics.
Nothing is shared between stores except the code and the infrastructure.

- **Which store:** every API request carries `X-Tenant-ID: <store>`. Each storefront's
  server-side BFF sets it from a constant (`STORE_ID`, `lib/api/config.ts`); the browser
  never chooses. A missing or unknown store is rejected with 400 — there is no default.
- **Database:** each service's `TenantFilter` puts the store in context; Hibernate's
  multi-tenant connection provider sets the connection's `search_path` to that schema.
  Flyway migrates every store schema on boot and creates a new store's schema automatically.
- **Auth:** user-service signs the store into the JWT (`tenant` claim). Every service rejects
  a token whose store differs from the request's, so a login is valid in one store only.
- **Async and background work:** Kafka records carry the store in an `X-Tenant-ID` header
  (stamped by the outbox pollers, restored by a record interceptor); outbox pollers and the
  analytics refresher loop over every store; Feign forwards the header; Redis catalog keys
  are prefixed with the store.
- **Code:** each service has its own copy of the `tenant` package (§15 — no shared module).
- **Adding a store:** append its id to `APP_TENANCY_TENANTS`, give user-service
  `APP_ADMINS_<STORE>_EMAIL` / `APP_ADMINS_<STORE>_PASSWORD` (the two existing stores use the
  shorter `MATESSA_ADMIN_*` / `ADVENTURE_ADMIN_*` aliases), restart. Schemas and the first
  admin are created on boot.
- **Upgrading a pre-store database:** `backend/db-init/upgrades/001-split-stores.sql` moves the
  old `public` data into the `matessa` schema (run once per DB, services stopped).

---

## 9. Docker & networking `[v1]`

- **Service name = hostname.** Inside the Docker network, containers reach each other by service name (e.g. `http://api-gateway:8080`, `jdbc:postgresql://orders-db:5432/orders`). `localhost` inside a container is *that container*, not the host — the most common beginner bug. Never hard-code `localhost` for inter-container addresses.
- **One shared bridge network** (e.g. `adventure-net`) so names resolve.
- **Only the gateway publishes a port to the host.** Everything else (services, DBs, Kafka, Redis) is reachable only inside the network — this is the first security boundary.
- **Config via `environment:` / env vars**, never hard-coded, so the same image runs in dev and prod.
- **`depends_on` controls start order, not readiness.** A dependency being "started" doesn't mean it's ready to accept connections — add health checks and connection retries (e.g. retry the DB/Kafka connection on boot).
- One `docker-compose.yml` boots the whole stack: eureka, gateway, the three services, three Postgres instances, Kafka, (later) Redis.

---

## 10. Kafka & async messaging `[v1 for order→stock]`

- **Use REST when you need an answer now; use Kafka when other services just need to know a fact happened.** Checkout asks commercial-service for price synchronously (Feign). Stock decrement, order history, and future email happen via the `OrderPlaced` event.
- **Producer:** order-service publishes `OrderPlaced` to a topic on successful checkout.
- **Consumers:** commercial-service (decrement stock), user-service (order history), future email-service (receipt). Each consumes independently.
- **At-least-once delivery is the default** → a consumer can see the same event twice.
  - **Consumers must be idempotent.** Reprocessing `OrderPlaced` must not decrement stock twice. The reference mechanism is a `processed_event` table keyed by event id (see `DATA-MODEL.md`). `[idempotency tracking: later, but design consumers to tolerate duplicates from v1]`
- **Reliable publishing — Transactional Outbox `[later]`:** to guarantee an event is never lost on a crash, write the business row + an `outbox` row in one DB transaction, then a poller publishes to Kafka. In v1 it is acceptable to publish directly to Kafka after commit (small risk of a lost event on crash); the schema leaves room to add the outbox later without a rewrite.

---

## 11. API gateway responsibilities `[v1]`

The gateway is the front door and does these jobs (so services don't each reinvent them):

- **Routing** — maps `/api/orders/**` → order-service via Eureka; clients never know instance addresses.
- **Auth gate** — validates the JWT signature + expiry once at the edge; rejects junk early.
- **CORS** — configured here, in one place, not per service.
- **Rate limiting** — throttle abusive clients (Redis-backed counters when Redis exists).
- **Cross-cutting** — request logging, correlation-id injection, response shaping.
- **Resilience** — timeouts and circuit breakers so one slow service can't sink the rest.

**Defense in depth:** the gateway validates the token, but **every service re-validates it too**. A service must be safe even if reached directly inside the network, not only through the gateway. Never write a service that trusts "the gateway already checked."

---

## 12. API design conventions `[v1]`

- **Resource nouns, plural:** `/api/products`, `/api/orders`, `/api/users`. No verbs in paths.
- **Verbs via HTTP method:** GET (read), POST (create), PUT/PATCH (update), DELETE.
- **Status codes mean things:** 201 created, 204 no content, 400 bad input, 401 missing/invalid token, 403 valid token but wrong role, 404 not found, 409 conflict (e.g. out of stock).
- **DTOs always** — never expose JPA entities directly (leaks schema, causes lazy-loading bugs). Map entity ↔ DTO (MapStruct is fine).
- **Validate input** with `@Valid` + Bean Validation.
- **Pagination from day one** on list endpoints (`Pageable`) — never return unbounded lists.
- **One consistent error-response shape** across all services.
- Example: `POST /api/orders` with `Authorization: Bearer <jwt>` → `201` with order number/total/status, or `409` if a variant is out of stock.

---

## 13. REST vs WebSocket `[REST v1, WebSocket later]`

- **REST for everything in v1.** Stateless, cacheable, simple, scales easily. Covers browse, checkout, login — almost all of Adventure Dose.
- **WebSocket only for genuine server-push features** `[later]`: live order tracking, support chat, an admin live dashboard, stock-drop alerts. Cost: stateful connections, harder to scale and load-balance — don't pay it until a feature truly needs a push.

---

## 14. Security model `[v1]`

### Token model
- **Stateless JWT, RS256 (asymmetric).** Only **user-service holds the private key** and signs tokens. Gateway and all services validate with the **public key**. Never use a shared HS256 secret across services.
- **Claims:** `sub` (userId), `roles`, `iat`, `exp`. Short-lived access token (~15 min) + refresh token.

### Token storage (two supported models — the filter handles both)
- **httpOnly + SameSite cookie (recommended for the browser storefront):** JS can't read it, so an XSS bug can't steal it; survives refresh. Requires CSRF protection (`SameSite=Strict/Lax` + CSRF token).
- **Bearer token in `Authorization` header (for future mobile / 3rd-party clients):** stored in JS memory (Zustand); immune to CSRF; but readable by JS and lost on refresh unless re-fetched via the refresh token.
- The JWT filter's `resolveToken` reads from **either** the `Authorization` header **or** the cookie, so both models work with one implementation.

### The JWT filter chain (what happens per request)
1. Request arrives with header or cookie.
2. JWT filter (`OncePerRequestFilter`) extracts and validates the token (RS256 signature + expiry).
3. On success, builds an `Authentication` (userId + roles) and sets it in the `SecurityContext`.
4. Authorization is enforced with method security, e.g. `@PreAuthorize("hasRole('MANAGER')")`.
5. Controller runs, trusting the verified identity.
- Missing/invalid token → filter short-circuits with **401** before any controller runs. Valid token, wrong role → **403**.
- **This filter is registered in every service**, not just the gateway (defense in depth).

### Roles & ownership
- Roles: `CUSTOMER`, `MANAGER`, `ADMIN` (see `DATA-MODEL.md` for scope). Role comes **only** from the verified JWT — never trust a role sent by the frontend.
- **Ownership ≠ role.** A valid customer token still must not read another customer's order. Enforce ownership checks in service logic, not just role annotations.

### Layered defense (each layer holds if the one before is bypassed)
1. **Network/edge** — only the gateway exposes a port; services unreachable from outside the Docker network.
2. **Gateway** — JWT check, CORS, rate limit, blocks junk before the mesh.
3. **Every service** — re-validates JWT, enforces roles, never assumes the gateway was the only entry.
4. **Data & secrets** — BCrypt password hashes (never store plaintext); RS256 private key only in user-service; secrets via env vars / vault, never in git; ownership checks.

### Other security hygiene
- Never log tokens or password hashes.
- CORS configured at the gateway only.
- Keep the private key out of source control.

---

## 15. Shared code & the common-lib question

**v1 decision: NO shared-code module.** Each service is self-contained and independently buildable. Small, understandable duplication (e.g. copying the ~40-line JWT filter into each service) is acceptable and preferred over premature coupling. Do not create a `common-lib` / `common-*` module unless the human explicitly asks.

**Why.** A shared-code module that contains domain code recouples the services through code: a model change forces every service to redeploy, which defeats the whole point of database-per-service. The microservice boundary we drew in the data layer must not be silently undone in the build layer.

**If/when a shared module is later requested**, it is governed by the full **"Shared code policy"** section in `DATA-MODEL.md` (the authoritative green/red lists). The short version:

- **May be shared (generic, no business domain):** the error-response DTO + `@RestControllerAdvice` handler, common exceptions, the JWT validation filter / resource-server config, a correlation-id filter, a pagination wrapper, and behavior-free constants (role names, Kafka topic names).
- **Never shared (recouples services):** entities (`User`, `Product`, `Order`, …) — this is the cardinal sin — repositories, any business/service logic, and cross-service client DTOs that encode another service's domain shape.
- **Name by concern, not catch-all:** `common-web`, `common-security` — never one grab-bag `common`. It is a library (no `@SpringBootApplication`, not registered with Eureka); dependency arrows point INTO it, never out.

**Sharing code ≠ sharing versions.** To keep dependency *versions* consistent (Spring Boot, springdoc, etc.), use a **parent POM / Gradle version catalog** — this shares no business code and carries none of the coupling risk. Reach for this *before* reaching for a shared-code module. In particular, do NOT centralize the springdoc/OpenAPI dependency in a shared module; each service declares it in its own build file.

**Extraction timing:** if duplication genuinely starts to hurt (the copied JWT filter has been edited in three places), extract a focused `common-security` module *from the real duplication then*. Extracting from lived duplication is safe; designing a shared lib upfront tends to attract junk.

---

## 16. Concepts the implementer should understand

These underpin the patterns above; an agent generating order-flow or infra code should know them:

- **Optimistic locking** (`@Version`) — prevents overselling.
- **Idempotent consumers** — safe under at-least-once Kafka delivery.
- **Transactional Outbox** — reliable event publishing without lost events `[later]`.
- **Soft deletes** — historical records never dangle.
- **Saga pattern (conceptual)** — compensating actions when a distributed step fails (e.g. order placed but stock unavailable → cancel/compensate), since there is no global transaction across services.
- **Snapshot vs live reference** — order items copy name/price at purchase; carts read live prices. (Detail in `DATA-MODEL.md`.)
- **Defense in depth** — security is layered, not a single wall.
