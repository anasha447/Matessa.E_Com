# Adventure Dose — Data Model Reference

> **Purpose:** Authoritative entity/relationship reference for AI agents (Claude Code, etc.) working on the Adventure Dose backend. This is the source of truth for entity shapes, ownership, and cross-service references. When code conflicts with this doc, this doc wins unless the human says otherwise.

## Architecture invariants (read first — these are non-negotiable)

1. **Database-per-service.** Each service owns its own database. No service reads or writes another service's tables. No cross-database JOINs. No cross-database foreign keys.
2. **Real FK vs. ID reference.** A relationship is a real DB-enforced foreign key ONLY when both entities live in the same service/database. Any relationship crossing a service boundary is a plain ID column (e.g. `userId`, `productVariantId`) that the application resolves — never a JPA `@ManyToOne` / FK constraint.
3. **Snapshots over links for historical records.** `Order` / `OrderItem` copy product name, SKU, price, coupon code, and shipping address as frozen-in-time values. They do NOT link live to commercial-service. An invoice must show what was bought and paid, even if the product later changes or is deactivated.
4. **Soft deletes, never hard deletes** on anything another service may reference (products, variants, users). Set `active = false`; never `DELETE`. This keeps historical orders pointing at rows that still exist.
5. **Money is `BigDecimal`, never `double`/`float`.** Floating point silently corrupts currency.
6. **All `@OneToMany` are `FetchType.LAZY`.** Eager fetching triggers the N+1 query problem and can load the whole catalog on one request.
7. **Stock lives only on `ProductVariant`.** There is no product-level inventory table. A product sold **without variations** still has stock: it carries one hidden *default* variant (`ProductVariant.defaultVariant = true`, no size/colour) created with the product, holding the product's own stock (`ProductRequest.stockQuantity` on create, then edited on that variant). Adding the first real variation deactivates the default; removing the last real variation reactivates it at stock 0. Cart, checkout and reservations therefore always work on `productVariantId` — one stock path.

## Services overview

| Service | Database | Owns | Notes |
|---|---|---|---|
| user-service | `users` | identity, roles, addresses, refresh tokens, support tickets | Issues JWTs (RS256). Source of truth for `userId`. |
| commercial-service | `commercial` | catalog, variants, images, reviews, coupons, discounts, wishlists, contact queries, subscribers | Source of truth for `productVariantId`, prices, stock. |
| order-service | `orders` | carts, orders, order items | Folds payment + shipping fields into `Order` (no separate service yet). |

Cross-cutting infra not modeled here: `eureka-server` (service discovery), `api-gateway` (routing + JWT validation + CORS). Any `common-lib` / shared module is governed by the "Shared code policy" section at the end of this doc — read it before creating one.

---

## user-service — database `users`

Suggested package: `com.adventuredose.user.*` → `entity`, `repository`, `service`, `controller`, `dto record `, `mapstruct`, `security`.

### User
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK, auto |
| email | String | unique, not null |
| passwordHash | String | not null (BCrypt) |
| firstName | String | |
| lastName | String | |
| phone | String | nullable |
| enabled | boolean | default true |
| createdAt | Instant | |
| updatedAt | Instant | |

Holds `Set<Role>` (enum: `ADMIN`, `MANAGER`, `CUSTOMER`) via an `@ElementCollection` or join table `user_roles`. A user may hold more than one role.

### Address
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| userId | Long | **FK → User.id** (same DB) |
| line1 | String | |
| line2 | String | nullable |
| city | String | |
| state | String | |
| postalCode | String | |
| country | String | |
| type | enum | HOME / WORK / OTHER |
| isDefault | boolean | |

### RefreshToken
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| userId | Long | **FK → User.id** |
| token | String | unique |
| expiresAt | Instant | |
| revoked | boolean | default false |

### SupportTicket
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| userId | Long | **FK → User.id** |
| orderId | Long | **ID ref → order-service** (not FK) |
| subject | String | |
| message | String | |
| status | enum | OPEN / IN_PROGRESS / CLOSED |
| createdAt | Instant | |

> Support tickets are optional for v1 — defer if scope is tight.

### Relationships (within users DB)
- `User 1—* Address`
- `User 1—* RefreshToken`
- `User 1—* SupportTicket`

---

## commercial-service — database `commercial`

Suggested package: `com.adventuredose.commercial.*`.

### Category
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| parentId | Long | **FK → Category.id** (self-ref, nullable; null = top level) |
| name | String | |
| slug | String | unique |
| description | String | nullable |

Self-referencing for nested categories (e.g. Clothes → Jackets). Top categories: Clothes, Helmets, Artwork.

### Product
The *concept* of a product. Does NOT hold stock.
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| categoryId | Long | **FK → Category.id** |
| name | String | |
| slug | String | unique |
| description | String | |
| basePrice | BigDecimal | not null |
| brand | String | nullable |
| active | boolean | default true (soft delete) |
| createdAt | Instant | |

### ProductVariant
**The unit actually stocked and purchased.**
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| productId | Long | **FK → Product.id** |
| sku | String | unique, not null |
| size | String | nullable |
| color | String | nullable |
| priceOverride | BigDecimal | nullable (null = use Product.basePrice) |
| stockQuantity | int | not null, >= 0 |
| defaultVariant | boolean | not null, default false; at most one per product (the product's own stock when it has no variations) |
| restockLevel | int | nullable (low-stock alert threshold) |
| version | Long | **`@Version` — optimistic lock**, prevents overselling |

### ProductImage
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| productId | Long | **FK → Product.id** |
| variantId | Long | **FK → ProductVariant.id**, nullable (null = general product photo; set = variant-specific) |
| url | String | either an external URL, or a `/media/products/*.webp` path for uploaded images |
| altText | String | nullable |
| displayOrder | int | |
| isPrimary | boolean | |

> **Image upload (added post-v1):** besides the URL+metadata `POST /api/products/{id}/images`, staff can upload a binary via `POST /api/products/{id}/images/upload` (multipart). commercial-service converts **any** uploaded format (PNG/JPEG/…) to **WebP** server-side (authoritative — can't be bypassed), stores it on a mounted volume, and serves it statically at `/media/**`. The resulting `.webp` path is saved as `url`.

### Review
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| productId | Long | **FK → Product.id** |
| userId | Long | **ID ref → user-service** (not FK) |
| rating | int | 1–5 |
| text | String | nullable |
| createdAt | Instant | |

### Coupon — *customer-facing, code-driven*
Shopper types a code at checkout. Has usage limits and min-spend.
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| code | String | unique, not null |
| discountType | enum | PERCENTAGE / FIXED_AMOUNT |
| discountValue | BigDecimal | |
| minSpend | BigDecimal | nullable |
| maxUses | int | nullable (total redemptions allowed) |
| usedCount | int | default 0 |
| perUserLimit | int | nullable |
| validFrom | Instant | |
| validUntil | Instant | |
| active | boolean | |

### Discount — *seller-driven, automatic*
Manager applies to a product **or** a category; no code needed; auto-applies. A category
discount **cascades to its sub-branches** (resolved via the product's category-ancestor
chain). Exactly one of `productId` / `categoryId` is set (enforced by a DB CHECK).
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| productId | Long | **FK → Product.id**, nullable — set = product-scoped (XOR categoryId) |
| categoryId | Long | **FK → Category.id**, nullable — set = category-scoped, cascades to sub-branches |
| type | enum | PERCENTAGE / FIXED_AMOUNT |
| value | BigDecimal | |
| validFrom | Instant | |
| validTo | Instant | |
| active | boolean | enable/disable without deleting |
| createdBy | Long | **ID ref → user-service** (manager) |

> **Coupon vs Discount distinction is intentional.** Coupon = customer enters a code, limited uses. Discount = automatic markdown a manager sets on catalog items. Different actor, different trigger.

### Wishlist / WishlistItem
| Wishlist field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| userId | Long | **ID ref → user-service** (not FK) |

| WishlistItem field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| wishlistId | Long | **FK → Wishlist.id** |
| productVariantId | Long | **ID ref → ProductVariant** (same DB here, but treat as catalog ref) |
| addedAt | Instant | |

### ContactQuery — *storefront support message*
| ContactQuery field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| email | String | not null (customer reply-to; guests may submit) |
| queryType | String | not null (the picked subject line) |
| message | String | not null (`text`) |
| status | Enum | `OPEN` / `RESOLVED`, default `OPEN` |
| createdAt | Instant | not null |

> Submitted from the storefront contact form (public `POST /api/contact`); staff (MANAGER/ADMIN) list and resolve. No cross-service link — `email` is a free-form contact, not a `User` ref.

### Subscriber — *newsletter signup → welcome coupon*
| Subscriber field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| email | String | not null, **unique** (idempotent signup) |
| couponCode | String | the minted welcome coupon's code (→ `Coupon.code`, same DB) |
| source | String | e.g. `popup` |
| createdAt | Instant | not null |

> A guest trades their email (public `POST /api/subscribers`) for a single-use 15% `Coupon` minted in the **same transaction**. A repeat email returns the existing coupon (no duplicate). Staff (MANAGER/ADMIN) list them.

### Outbox + idempotency (phase 2 — defer if tight)
- **`outbox`** — `id`, `eventType`, `payload (JSON)`, `published (boolean)`, `createdAt`. Written in the SAME transaction as the business change; a poller publishes to Kafka. (Transactional Outbox pattern.)
- **`processed_event`** — `eventId (PK)`, `processedAt`. Checked before acting on a consumed event so re-delivered messages are ignored. (Idempotent consumer.)

### Relationships (within commercial DB)
- `Category 1—* Category` (self, parent)
- `Category 1—* Product`
- `Product 1—* ProductVariant`
- `Product 1—* ProductImage`
- `ProductVariant 1—* ProductImage`
- `Product 1—* Review`
- `Product 1—* Discount` (product-scoped)
- `Category 1—* Discount` (category-scoped; cascades to sub-branches)
- `Wishlist 1—* WishlistItem`
- `Subscriber *—1 Coupon` (by `couponCode`, same DB — not an FK)

---

## order-service — database `orders`

Suggested package: `com.adventuredose.order.*`.

### Cart
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| userId | Long | **ID ref → user-service** (not FK) |
| createdAt | Instant | |
| updatedAt | Instant | |

### CartItem
Price is NOT stored — the cart shows live prices fetched from commercial-service.
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| cartId | Long | **FK → Cart.id** |
| productVariantId | Long | **ID ref → commercial-service** (not FK) |
| quantity | int | |

### Order
Payment + shipping folded in as fields/snapshots (no separate service yet).
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| userId | Long | **ID ref → user-service** (not FK) |
| orderNumber | String | unique |
| status | enum | PENDING / PAID / SHIPPED / DELIVERED / CANCELLED |
| subtotal | BigDecimal | |
| discountAmount | BigDecimal | |
| total | BigDecimal | |
| couponCode | String | **snapshot** (nullable) |
| shippingAddressSnapshot | String/JSON | **snapshot**, not an FK |
| shippingState | String | promoted from the snapshot at checkout (trimmed, blank→null); indexed for orders-by-state analytics |
| paymentStatus | enum | PENDING / COMPLETED / FAILED / REFUNDED |
| paymentMethod | enum | CARD / PAYPAL / ... |
| transactionId | String | nullable |
| shippingStatus | enum | PENDING / SHIPPED / DELIVERED / RETURNED |
| shippingMethod | String | nullable |
| createdAt | Instant | |

### OrderItem
**Everything here is frozen at purchase time.**
| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| orderId | Long | **FK → Order.id** |
| productVariantId | Long | **ID ref → commercial-service** (not FK) |
| productNameSnapshot | String | snapshot |
| skuSnapshot | String | snapshot |
| unitPriceSnapshot | BigDecimal | snapshot |
| quantity | int | |

### Outbox (phase 2 — defer if tight)
- **`outbox`** — same shape as commercial-service outbox. On checkout, write `Order` + outbox row in one transaction, then poll → publish `OrderPlaced` to Kafka.

### Relationships (within orders DB)
- `Cart 1—* CartItem`
- `Order 1—* OrderItem`

---

## Cross-service references (NO foreign keys — application-resolved)

| From | Field | Points at | Resolved by |
|---|---|---|---|
| order-service `Cart` / `Order` | userId | user-service `User` | JWT subject / Feign call |
| order-service `CartItem` / `OrderItem` | productVariantId | commercial `ProductVariant` | Feign (price/stock at order time) |
| commercial `Review` | userId | user-service `User` | JWT / Feign |
| commercial `Discount` | createdBy | user-service `User` | JWT |
| commercial `Wishlist` | userId | user-service `User` | JWT |
| user-service `SupportTicket` | orderId | order-service `Order` | Feign |

## Key event flow (sync + async)

1. Customer checks out → order-service calls commercial-service **synchronously (Feign)** to validate stock and get current price → copies price into `OrderItem` snapshots.
2. order-service writes `Order` + `outbox(OrderPlaced)` in one transaction.
3. Poller publishes `OrderPlaced` to Kafka.
4. commercial-service consumes `OrderPlaced`, checks `processed_event` for idempotency, decrements `ProductVariant.stockQuantity` using the `version` optimistic lock, records the `eventId` as processed.

## Concepts an implementer should know before coding the order flow
- Optimistic locking (`@Version`) — prevents overselling the last item.
- Transactional Outbox — reliable event publishing without losing events on crash.
- Idempotent consumer (`processed_event`) — safe under at-least-once delivery.
- Soft deletes (`active` flag) — historical orders never point at vanished rows.
- Saga pattern (conceptual) — compensating actions when a distributed step fails (e.g. order placed but stock unavailable → cancel order).

---

## Shared code policy (common-lib) — READ BEFORE creating any shared module

> **Agent rule:** Do NOT create a `common-lib` module or move code into a shared module unless the human explicitly asks for it. Default to keeping each service self-contained, even if that means small, understandable duplication (e.g. copying the JWT filter). When a shared module IS requested, obey the green/red lists below exactly.

### The one principle
A shared library is acceptable ONLY for code that is **generic and has nothing to do with the business domain**. The moment it contains a domain entity or business logic, every service couples to it, a model change forces all services to redeploy, and the microservice split is defeated. The *what* matters far more than the *whether*.

> **Litmus test:** If changing a class in the shared module would force more than one service to redeploy for a *business* reason, that class is in the wrong place. Move it back into the owning service.

### Allowed in a shared module (GREEN — generic cross-cutting only)
- Standard API error-response DTO (so all services return errors in one shape).
- `@RestControllerAdvice` global exception handler + common exceptions (e.g. `ResourceNotFoundException`, `BadRequestException`).
- JWT validation filter / resource-server security config (every service validates the same RS256 token the same way — the single strongest candidate to share).
- Correlation-ID propagation filter (for distributed tracing/logging).
- Pagination response wrapper / generic page DTO.
- Constants with no behavior: role names (`ROLE_ADMIN`...), Kafka topic names, header names.

### FORBIDDEN in a shared module (RED — recouples services)
- **Entities** (`User`, `Product`, `ProductVariant`, `Order`, ...). Each is the private model of one service. Sharing entities is the cardinal sin — it recouples databases through code. NEVER do this.
- Repositories (`JpaRepository` interfaces) and any persistence config tied to a specific schema.
- Service-layer / business logic of any kind.
- Cross-service client DTOs that encode another service's domain shape (prefer a thin, service-local DTO that maps only the fields actually consumed).
- The Swagger/OpenAPI dependency itself. Each service declares `springdoc-openapi` in its OWN build file. Centralizing the *dependency* in shared code adds a version bottleneck for no benefit.

### Sharing CODE vs. sharing VERSIONS — keep these separate
These are two different mechanisms and people conflate them:
- **Sharing code** = a `common-lib` JAR every service depends on. High coupling risk. Use only for the GREEN list.
- **Sharing versions** = a parent POM (Maven) or version catalog (Gradle) that pins dependency versions (Spring Boot, springdoc, etc.) without sharing a single line of business code. Low risk, almost always good. **This is the correct tool for "manage Swagger/dependency versions in one place."** Reach for this before reaching for a shared-code module.

### Recommended sequencing for THIS project (junior, first microservice system)
1. **v1:** No shared-code module. Each service self-contained and independently buildable. Accept copying the ~40-line JWT filter into each service. Optionally introduce a **parent POM / version catalog** for version consistency only.
2. **Later, when the duplication actually hurts** (you've changed the copied JWT filter and had to edit it in 3 places): extract a `common-security` module from the *real* duplication. Extracting from lived duplication is easy and safe; designing a shared lib upfront tends to attract junk.

### If/when a shared module is created — suggested shape
- Name by concern, not "common-everything": e.g. `common-web` (error DTO + handler), `common-security` (JWT filter), rather than one catch-all `common`.
- It is a library only — no `@SpringBootApplication`, no `main`, not a running service, not registered with Eureka.
- It must depend on nothing in any business service. Dependency arrows point INTO it, never out.
- Keep it small. If it grows past cross-cutting concerns, that growth is the warning sign.
