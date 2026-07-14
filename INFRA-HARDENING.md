# Infra Hardening — Observability, Caching, Reliability

Status of the 8-phase gap-analysis roadmap. Phases 1–7 are implemented in this repo; Phase 8
items are **prod-target designs** (config authored here, deployed in a real environment — they
don't fit the local 4 GB Docker VM).

## Implemented (in-repo)

| Phase | What | Where / how to operate |
|---|---|---|
| 1 · Distributed tracing | OTel bridge + OTLP export to Tempo; Prometheus metrics + JSON-log instrumentation on gateway/user/commercial/order | Deps in each `pom.xml`; `management.*` in each `application.yaml`; `redis`/`tempo`/`grafana` in compose. Live span export verified — all four services reach Tempo and a single request produces one cross-service trace (see note). Grafana at `:3001`, Tempo at `:3200`. |
| 2 · Redis catalog cache | Cache-aside on product/variant/category reads; in-process `@CacheEvict` on every write (adds/edits show next read); `getPriceStock` never cached (stock stays authoritative) | `commercial-service` `CacheConfig`; keys `cat:v1:*` in Redis |
| 3 · Idempotency keys | `Idempotency-Key` on checkout; DB-unique `idempotency_key` (durable) + fast-return on repeat | `order-service` V3 migration + `OrderController`/`createOrder`; FE `CheckoutForm` + BFF forward |
| 4 · Edge rate limiting | Redis fixed-window per-IP on login/guest/track/price; 429 + Retry-After; fails open | `api-gateway` `RateLimitFilter`; keys `rl:*` |
| 5 · Kafka DLQ + retry | `DefaultErrorHandler` + DLT; transient→2 retries then `<topic>-dlt`; poison→straight to DLT | `commercial-service` `KafkaErrorHandlerConfig` |
| 6 · CQRS analytics read model | Materialized views for the heavy dashboard aggregations; scheduled `REFRESH … CONCURRENTLY` | `order-service` V4 migration + `AnalyticsViewRefresher`; analytics repo reads `mv_*` |
| 7 · Immutable media caching | `Cache-Control: max-age=31536000, public, immutable` on `/media/**` (content-hashed filenames) | `commercial-service` `MediaWebConfig` |

**Note (Phase 1):** live span export works on Spring Boot 4.0.6. Boot 4.0 restructured the tracing
wiring vs 3.x, which took two fixes: (1) depend on `spring-boot-starter-opentelemetry` — the
`micrometer-tracing-bridge-otel` artifact alone no longer pulls the tracing auto-config (Tracer
bean + OTLP exporter); and (2) the OTLP endpoint property moved to
`management.opentelemetry.tracing.export.otlp.endpoint` (sampling stays at
`management.tracing.sampling.probability`). The starter also bundles an OTLP *metrics* registry we
exclude (`micrometer-registry-otlp`), since metrics are scraped via Prometheus (pull) — otherwise
it spams connection errors pushing to a non-existent collector. Verified via Tempo on two paths: (1) sync HTTP —
a single `/api/products` call yields one trace spanning api-gateway → commercial-service; (2) async
Kafka — a live COD guest order yields one trace spanning order-service's `outboxPoller.publishPending`
→ `OrderPlaced send` (producer) → commercial-service `OrderPlaced process` (consumer), confirming
trace-context propagation across the broker via `observation-enabled: true` on the template +
listener. (The Kafka trace is rooted at the poller, not the original HTTP request — the transactional
-outbox poll is decoupled by design; linking back would need `traceparent` stored on the outbox row.)
Metrics endpoints (`/actuator/prometheus`) and JSON logs are active; Prometheus/Loki backends are
Phase 8.

## Phase 8 — prod-target designs

### Kafka HA (currently 1 broker, RF=1 — a broker loss = event loss)
3-broker KRaft cluster; per-topic `RF=3`, `min.insync.replicas=2`; producers `acks=all`:
```yaml
spring.kafka.producer.acks: all
spring.kafka.producer.properties.enable.idempotence: true
# topic creation: --replication-factor 3 --config min.insync.replicas=2
```

### Secrets (currently `.env` + PEM on a volume)
- DB creds + JWT keys → Vault / cloud KMS (never in `.env` or images).
- Replace the static `jwt-public.pem` with a **JWKS** endpoint on user-service
  (`/.well-known/jwks.json`) so the gateway/services rotate keys without redeploys:
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://user-service:8081/.well-known/jwks.json`.

### Outbox CDC (currently a 2 s polling `OutboxPoller`)
Debezium Postgres connector on the `outbox` table → guaranteed, ordered publish; drop the poller:
```json
{ "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
  "transforms": "outbox",
  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
  "table.include.list": "public.outbox" }
```

### Metrics + logs backends (instrumentation already emits them)
- **Prometheus** scrapes each service's `/actuator/prometheus`; dashboards + alerts in Grafana.
- **Loki** ingests the JSON logs (trace-id correlated) alongside Tempo traces in one Grafana.

### Also worth doing
- Bulkheads + retry-with-jitter on the `order→commercial` Feign path (Resilience4j `@Bulkhead`).
- Idempotency Redis fast-path (Phase 3 is DB-only today; add a Redis response cache for speed).
- Horizontal scale: run 2× gateway, an Eureka peer pair, Postgres primary+replica per service
  (K8s HPA on latency). Local scaling demo: `docker compose up -d --scale commercial-service=2`
  (drop its `container_name` + host port first).
