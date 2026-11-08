# Matessa

A production-shaped e-commerce platform: Spring Boot microservices behind an API
gateway, with Next.js storefronts in front. Built as a deliberate exercise in
distributed-system patterns — service discovery, database-per-service, RS256 JWT
across a service mesh, event-driven stock handling, and edge rate limiting.

> **Status.** The single-store platform works end to end: catalog, cart, guest and
> registered checkout, coupons, order tracking, reviews, wishlists and an admin
> panel. The platform is currently being extended to host **two independent
> storefronts on one backend** — separate data, separate customers, separate auth.
> See [Roadmap](#roadmap).

---

## Architecture

```
                    browser
                       │
        ┌──────────────┴──────────────┐
        │                             │
   frontend :3100              second-frontend :3000
   (Next.js, BFF)               (Next.js, BFF)
        │                             │
        └──────────────┬──────────────┘
                       │  server-side only
                       ▼
               api-gateway :9080          ◄── the only public door
            · RS256 JWT validation
            · CORS, Redis rate limiting
            · circuit breakers + fallbacks
                       │  lb:// via Eureka
      ┌────────────────┼────────────────┐
      ▼                ▼                ▼
 user-service    commercial-service  order-service
   :9081             :9082              :9083
      │                ▲ │                │
      │         Feign ──┘ │ Kafka         │
      │       (price/stock)│ OrderPlaced  │
      ▼                ▼  ▼              ▼
   users DB       commercial DB      orders DB
        └──────── one Postgres, three databases,
                  three owning roles ────────┘

         eureka-server :9761   ·   redis :6380   ·   kafka
```

**Why it looks like this**

- **Database per service.** Each service owns its schema and nobody reads anyone
  else's tables. Cross-service relationships are ID references resolved with Feign
  (synchronous) or Kafka events (asynchronous) — never a join. The three databases
  share one Postgres *process* to fit a small host, but keep separate databases and
  separate owning roles, so the boundary is real and not just a convention.
- **Defence in depth on auth.** user-service holds the RS256 private key and is the
  only signer. The gateway *and* every service independently validate the token with
  the public key — no service trusts "the gateway already checked". Role comes only
  from the verified JWT; ownership ("is this the caller's own order?") is enforced
  separately in service logic.
- **Tokens never reach browser JavaScript.** Each Next app is its own BFF: the
  browser calls same-origin `/api/bff/*`, the Next server attaches the token from an
  httpOnly cookie and proxies to the gateway. An XSS bug cannot read a token.
- **Stock is event-driven.** Checkout emits `OrderPlaced` through a transactional
  outbox; commercial-service consumes it and adjusts stock under optimistic locking,
  emitting `OrderRejected` if it can't.

## Stack

| Layer | Choice |
|---|---|
| Services | Spring Boot 4, Java 25, Spring Cloud Gateway (webmvc), Eureka |
| Data | PostgreSQL 15, Flyway migrations, JPA/Hibernate, MapStruct, Lombok |
| Messaging | Kafka (KRaft, single node) with a transactional outbox |
| Resilience | Resilience4j circuit breakers on Feign + gateway routes |
| Edge | Redis fixed-window rate limiting, CORS, circuit-breaker fallbacks |
| Frontend | Next.js 16 (App Router), React 19, Tailwind 4, TypeScript |
| Tests | JUnit 5, Testcontainers (real Postgres + Kafka), WireMock |
| Runtime | Docker Compose, 10 containers |

## Ports

Host ports are offset so this stack can run alongside another on one machine.
Container-internal ports keep conventional values (8080–8083, 8761, 5432, 6379).

| Service | Host | Service | Host |
|---|---|---|---|
| api-gateway | 9080 | eureka-server | 9761 |
| user-service | 9081 | postgres | 5533 |
| commercial-service | 9082 | redis | 6380 |
| order-service | 9083 | pgAdmin (`tools`) | 5050 |
| frontend | 3100 | second-frontend | 3000 |

## Running it

```bash
cp env.example .env          # then edit the passwords
```

Generate the RS256 key pair (gitignored, never baked into an image):

```bash
mkdir -p keys && openssl genpkey -algorithm RSA -out keys/jwt-private.pem -pkeyopt rsa_keygen_bits:2048 && openssl rsa -pubout -in keys/jwt-private.pem -out keys/jwt-public.pem
```

**Backend only** (storefronts run on the host with `npm run dev`):

```bash
docker compose up -d
```

**Everything, production-style** (builds real images):

```bash
docker compose --profile prod up -d --build
```

**Fast iteration** — runs the JVM services from source with bind mounts and a shared
Maven cache, so a code change doesn't need an image rebuild:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

**On-demand database UI:**

```bash
docker compose --profile tools up -d pgadmin
```

## Repo layout

```
backend/
  eureka-server/        service registry
  api-gateway/          public entry point: routing, JWT, CORS, rate limiting
  user-service/         accounts, addresses, RS256 token issuing, staff admin
  commercial-service/   catalog, variants, images, reviews, coupons, wishlists
  order-service/        cart pricing, checkout, orders, shipping, tracking
  db-init/              first-boot role/database creation
frontend/               storefront #1 (Matessa) + admin panel
second-frontend/        storefront #2
observability/          Grafana/Tempo config (tracing currently disabled)
```

Reference docs: [ARCHITECTURE.md](ARCHITECTURE.md) ·
[DATA-MODEL.md](DATA-MODEL.md) · [API.md](API.md) ·
[BRAND.md](BRAND.md) · [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md)

## Is this over-engineered?

For the traffic it serves, yes — and that's deliberate. A single Spring Boot app with
one database would serve this catalog comfortably and would be less work to operate.
The split exists because the point was to learn the patterns that only appear once
you have a network between your components: discovery, partial failure, eventual
consistency, token propagation across trust boundaries, and schema ownership.

What the split genuinely bought: a stock-adjustment path that survives
commercial-service being down, independent migration histories, and a security model
where no single bypass is sufficient.

What it cost, honestly: a checkout that spans two services and a Kafka hop is much
harder to reason about than a transaction; three Flyway histories mean three places
to look; and running it needs 10 containers rather than two. At this scale a modular
monolith with the same internal boundaries would have been the pragmatic call, and
splitting later — once a module actually needed to scale independently — would have
been straightforward.

## Roadmap

Multi-tenancy, so one backend serves both storefronts with fully separate data:

- Schema per tenant inside each service's own database
- Tenant resolved **server-side from the request host at the gateway**, which strips
  any client-supplied tenant header — the browser never names its own tenant
- Tenant bound into the JWT as a claim, so a token from one store is rejected by the
  other even if the header layer were bypassed
- Tenant propagated across Feign and Kafka boundaries
