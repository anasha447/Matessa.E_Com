# API.md — Adventure Dose Backend (for the frontend agent)

> **Read this before building any screen.** This is the contract between the Next.js frontend and the Spring Boot backend. It covers the base URL, auth, conventions, every endpoint with its request/response shape, the role matrix, and behavioural notes you need to build a correct store. Pairs with `BRAND.md` (visual/voice) and `ARCHITECTURE.md` (how the backend is built).
>
> Live, always-current docs: each service also serves **Swagger UI** and an **OpenAPI spec** (see §1). A ready-to-import **Postman collection** lives at `postman/AdventureDose.postman_collection.json`.

---

## 1. How to reach the backend

- **One entry point: the API Gateway at `http://localhost:9080`.** Send *every* request here. The individual services (host ports 9081/9082/9083) are internal; only hit them directly for Swagger in local dev.
- All requests and responses are **JSON** (`Content-Type: application/json`).
- **CORS** is preconfigured for the Next.js dev origin **`http://localhost:3000`** (credentials allowed; methods GET/POST/PUT/PATCH/DELETE/OPTIONS). If you change the frontend port, the gateway CORS config must change too.
- Run the backend: `docker compose up -d` (gateway + 3 services + 3 Postgres + Kafka). Seeded admin: **`admin@adventuredose.com` / `Admin123!Dose`**.
- **Swagger UI** (dev): `:9081/swagger-ui.html` (user), `:9082/swagger-ui.html` (commercial), `:9083/swagger-ui.html` (order). OpenAPI JSON: `:<port>/v3/api-docs` — you can codegen a typed client from these.

---

## 2. Authentication & roles

**RS256 JWT, header-based.** Flow:

1. `POST /api/auth/register` → creates a `CUSTOMER` account.
2. `POST /api/auth/login` → returns `{ accessToken, refreshToken, tokenType: "Bearer", expiresIn }`.
3. Send **`Authorization: Bearer <accessToken>`** on every authenticated request.
4. When the access token expires (~15 min → `401`), call `POST /api/auth/refresh { refreshToken }` to get a fresh pair. Refresh tokens last ~7 days.
5. `POST /api/auth/logout { refreshToken }` revokes the refresh token.

- The access token is a JWT whose **`sub` = userId** and **`roles`** claim = e.g. `["CUSTOMER"]`. You can decode it client-side to read the user id / roles for UI gating (but never trust it for security — the backend re-checks).
- *(Optional)* the backend also accepts the token in an **`access_token` cookie** if you prefer a cookie strategy; the default/recommended path is the Bearer header.

**Roles** (a user can hold several):

| Role | Can do |
|---|---|
| `CUSTOMER` | browse, cart, checkout, own orders, reviews, wishlist |
| `MANAGER` | everything a customer can + manage catalog (categories/products/variants/images), coupons, discounts, all orders, and **sales + catalog analytics** |
| `ADMIN` | everything MANAGER can + **customer analytics** (user data) |

There is **no public signup for staff** — MANAGER/ADMIN are granted in the DB / via the seeded admin.

---

## 3. Conventions

**Pagination** — list endpoints accept `?page=0&size=20&sort=field,asc`. The response is a Spring `PagedModel`:
```json
{ "content": [ /* items */ ],
  "page": { "size": 20, "number": 0, "totalElements": 42, "totalPages": 3 } }
```

**Money** — JSON numbers with 2 decimal places (server uses `BigDecimal`). Treat as strings/decimals on the client; don't use floats for arithmetic.

**Errors** — one consistent shape across all services:
```json
{ "timestamp": "2026-06-10T12:00:00Z", "status": 409, "error": "Conflict",
  "message": "Variant SKU already exists: TH-RED-M", "path": "/api/products/5/variants",
  "fieldErrors": { "rating": "must be less than or equal to 5" } }
```
`fieldErrors` (field → message) is present on `400` validation failures; otherwise omitted.

**Status codes**

| Code | Meaning for the frontend |
|---|---|
| `200` | OK |
| `201` | Created (POST) — body is the created resource |
| `204` | No Content (DELETE / logout) |
| `400` | Validation / bad input — read `fieldErrors` |
| `401` | Missing/expired token → **refresh and retry**, else send to login |
| `403` | Authenticated but wrong role |
| `404` | Not found — also returned for *someone else's* resource (ownership is masked) |
| `409` | Conflict — duplicate slug/SKU, **out of stock at checkout**, coupon already used, concurrent edit |
| `503` | A downstream service (catalog) is momentarily unavailable — show "try again" |

**Ownership** — a customer only ever sees their own cart, orders, wishlist, and editable reviews; another user's resource returns `404` (never leaks existence).

**Public vs authed** — the gateway lets these through **without a token**: `POST /api/auth/**`, and **GET** on `/api/categories`, `/api/products`, `/api/variants`, `/api/reviews`, `/api/discounts` (i.e. catalog browsing + reading reviews/discounts). Everything else needs a valid token.

---

## 4. Endpoints

Legend for **Auth**: `public` = no token · `auth` = any logged-in user · `owner` = the resource owner · `staff` = MANAGER or ADMIN · `admin` = ADMIN only.

### 4.1 Auth & account  *(user-service)*
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| POST | `/api/auth/register` | public | `RegisterRequest` | `201 UserResponse` (409 if email taken) |
| POST | `/api/auth/login` | public | `LoginRequest` | `TokenResponse` (401 bad creds) |
| POST | `/api/auth/refresh` | public | `{ refreshToken }` | `TokenResponse` |
| POST | `/api/auth/logout` | public | `{ refreshToken }` | `204` |
| GET | `/api/users/me` | auth | — | `UserResponse` |
| PUT | `/api/users/me` | auth | `UpdateProfileRequest` | `UserResponse` (edit own name/phone) |
| PUT | `/api/users/me/password` | auth | `ChangePasswordRequest` | `204` (400 if current password wrong) |
| GET | `/api/users/me/addresses` | auth | — | `Page<AddressResponse>` |
| POST | `/api/users/me/addresses` | auth | `AddressRequest` | `201 AddressResponse` |
| PUT | `/api/users/me/addresses/{id}` | owner | `AddressRequest` | `AddressResponse` |
| DELETE | `/api/users/me/addresses/{id}` | owner | — | `204` |

### 4.2 Catalog — categories  *(commercial-service)*
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| GET | `/api/categories` | public | — | `CategoryResponse[]` (top-level) |
| GET | `/api/categories/tree` | public | — | `CategoryResponse[]` (nested `children`) |
| GET | `/api/categories/{id}` | public | — | `CategoryResponse` |
| GET | `/api/categories/{id}/children` | public | — | `CategoryResponse[]` |
| POST | `/api/categories` | staff | `CategoryRequest` | `201 CategoryResponse` |
| PUT | `/api/categories/{id}` | staff | `CategoryRequest` | `CategoryResponse` |
| DELETE | `/api/categories/{id}` | staff | — | `204` (soft delete) |

### 4.3 Catalog — products
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| GET | `/api/products?categoryId=&page=&size=` | public | — | `Page<ProductResponse>` |
| GET | `/api/products/{id}` | public | — | `ProductResponse` |
| POST | `/api/products` | staff | `ProductRequest` | `201 ProductResponse` |
| PUT | `/api/products/{id}` | staff | `ProductRequest` | `ProductResponse` |
| DELETE | `/api/products/{id}` | staff | — | `204` (soft delete) |

### 4.4 Catalog — variants
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| GET | `/api/products/{productId}/variants` | public | — | `ProductVariantResponse[]` |
| POST | `/api/products/{productId}/variants` | staff | `ProductVariantRequest` | `201 ProductVariantResponse` |
| GET | `/api/variants/{id}` | public | — | `VariantPriceStockResponse` (live price/stock — use on PDP & cart) |
| PUT | `/api/variants/{id}` | staff | `ProductVariantRequest` | `ProductVariantResponse` |
| DELETE | `/api/variants/{id}` | staff | — | `204` (soft delete) |

### 4.5 Catalog — images
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| GET | `/api/products/{productId}/images` | public | — | `ProductImageResponse[]` |
| POST | `/api/products/{productId}/images` | staff | `ProductImageRequest` | `201 ProductImageResponse` |
| PUT | `/api/images/{id}` | staff | `ProductImageRequest` | `ProductImageResponse` |
| DELETE | `/api/images/{id}` | staff | — | `204` |

> Images store a **URL + metadata** (no binary upload in v1). `variantId` null = general product photo; set = scoped to a colour/size variant. At most one `primary` per product.

### 4.6 Reviews
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| GET | `/api/products/{productId}/reviews?page=&size=` | public | — | `Page<ReviewResponse>` |
| GET | `/api/products/{productId}/reviews/summary` | public | — | `ReviewSummaryResponse` (avg + count) |
| POST | `/api/products/{productId}/reviews` | auth | `ReviewRequest` | `201 ReviewResponse` (409 if already reviewed) |
| PUT | `/api/reviews/{id}` | owner | `ReviewRequest` | `ReviewResponse` |
| DELETE | `/api/reviews/{id}` | owner / staff | — | `204` |

> One review per (user, product). `rating` is 1–5.

### 4.7 Wishlist  *(private to the owner)*
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| GET | `/api/wishlist` | auth | — | `WishlistResponse` (auto-created) |
| POST | `/api/wishlist/items` | auth | `{ productVariantId }` | `201 WishlistItemResponse` (409 if dup) |
| DELETE | `/api/wishlist/items/{id}` | owner | — | `204` |

### 4.8 Coupons & discounts
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| GET | `/api/coupons?page=&size=` | staff | — | `Page<CouponResponse>` |
| GET | `/api/coupons/{id}` | staff | — | `CouponResponse` |
| POST | `/api/coupons` | staff | `CouponRequest` | `201 CouponResponse` |
| PUT | `/api/coupons/{id}` | staff | `CouponRequest` | `CouponResponse` |
| DELETE | `/api/coupons/{id}` | staff | — | `204` (soft) |
| POST | `/api/coupons/validate` | auth | `{ code, subtotal }` | `CouponValidationResponse` (checkout preview) |
| GET | `/api/products/{productId}/discounts` | public | — | `DiscountResponse[]` (auto-applied discounts for a product) |
| GET | `/api/discounts/{id}` | public | — | `DiscountResponse` |
| POST | `/api/discounts` | staff | `DiscountRequest` | `201 DiscountResponse` |
| PUT | `/api/discounts/{id}` | staff | `DiscountRequest` | `DiscountResponse` |
| DELETE | `/api/discounts/{id}` | staff | — | `204` |

> **Discounts vs coupons:** *discounts* are automatic markdowns set by staff on a product **or** a category (cascades to sub-branches) and are already baked into `effectivePrice`. *Coupons* are codes the shopper types at checkout — validate with `/api/coupons/validate` to preview the amount off.

### 4.9 Cart  *(live-priced, owner-only)*
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| GET | `/api/cart` | auth | — | `CartResponse` |
| POST | `/api/cart/items` | auth | `{ productVariantId, quantity }` | `201 CartResponse` (merges qty for a repeat variant) |
| PUT | `/api/cart/items/{id}` | auth | `{ quantity }` | `CartResponse` |
| DELETE | `/api/cart/items/{id}` | auth | — | `204` |
| DELETE | `/api/cart` | auth | — | `204` (clear) |

> The cart fetches **live, discount-aware prices** from the catalog on every read. If the catalog is briefly down, a line comes back `available:false` with null price (render it greyed-out, keep the rest of the cart).

### 4.10 Orders & checkout
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| POST | `/api/orders` | auth | `CheckoutRequest` | `201 OrderResponse` (**409 if a line is out of stock**) |
| GET | `/api/orders?page=&size=` | auth | — | `Page<OrderSummaryResponse>` (own, newest first) |
| GET | `/api/orders/{id}` | owner / staff | — | `OrderResponse` |
| POST | `/api/orders/{id}/cancel` | owner / staff | — | `OrderResponse` (only PENDING/PAID; 409 if shipped) |
| GET | `/api/orders/{id}/history` | owner / staff | — | `OrderStatusHistoryResponse[]` |
| GET | `/api/orders/admin?status=&page=` | staff | — | `Page<OrderSummaryResponse>` (all orders) |
| PUT | `/api/orders/{id}/status` | staff | `{ status, note? }` | `OrderResponse` |
| PUT | `/api/orders/{id}/shipping` | staff | `{ shippingMethod, trackingNumber? }` | `OrderResponse` |

> **Checkout flow for the UI:** (1) build the cart, (2) optionally `POST /api/coupons/validate` to show the discount, (3) `POST /api/orders` with the inline shipping address + payment method (+ coupon code). Payment is **mocked** in v1: `CARD`/`PAYPAL` settle instantly (`status: PAID`), `COD` stays `PENDING`. Stock is decremented **asynchronously** just after the order is placed — so don't assume the catalog stock dropped the instant checkout returns.

### 4.11 Analytics  *(admin/manager dashboard data)*
| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/analytics/sales` | staff | `SalesAnalyticsResponse` |
| GET | `/api/analytics/sales/revenue-trend?months=12` | staff | `RevenueTrendPoint[]` |
| GET | `/api/analytics/sales/top-products?limit=10` | staff | `TopProductResponse[]` |
| GET | `/api/analytics/sales/top-customers?limit=10` | staff | `TopCustomerResponse[]` |
| GET | `/api/analytics/sales/orders-by-state` | staff | `StateOrderCountResponse[]` — orders per shipping state (India map); excludes CANCELLED; null state grouped as `"Other"` |
| GET | `/api/analytics/catalog` | staff | `CatalogAnalyticsResponse` |
| GET | `/api/analytics/catalog/low-stock?limit=20` | staff | `LowStockItemResponse[]` |
| GET | `/api/analytics/catalog/top-rated?limit=10` | staff | `TopRatedProductResponse[]` |
| GET | `/api/analytics/catalog/most-wishlisted?limit=10` | staff | `MostWishlistedResponse[]` |
| GET | `/api/analytics/customers` | **admin** | `CustomerAnalyticsResponse` |

### 4.12 Admin — users & staff  *(user-service, ADMIN only)*
| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| POST | `/api/admin/users` | **admin** | `CreateStaffRequest` | `201 AdminUserResponse` (409 if email taken) — provisions a **MANAGER** with a temp password |
| GET | `/api/admin/users?role=&enabled=&page=&size=` | **admin** | — | `Page<AdminUserResponse>` (directory; both filters optional) |
| GET | `/api/admin/users/{id}` | **admin** | — | `AdminUserResponse` |
| PUT | `/api/admin/users/{id}/status` | **admin** | `UpdateUserStatusRequest` | `AdminUserResponse` (ban/un-ban; **409** if you target your own account) |

> **Provisioning staff:** an admin creates a manager with `POST /api/admin/users`; the new account is `enabled:true` with `mustChangePassword:true`. The manager logs in with the temp password, and the UI should force them through `PUT /api/users/me/password` (which clears the flag). **Banning** (`enabled:false`) blocks both login and refresh and revokes the user's refresh tokens — but a still-valid access token keeps working until it expires (≤ `expiresIn` seconds), so don't treat a ban as instant session kill. The directory (`GET /api/admin/users`) is also the only way to list customers (`?role=CUSTOMER`). There is no API to create or revoke ADMINs or to delete users in v1.

---

## 5. Schema reference

**Enums**
- `Role`: `CUSTOMER` · `MANAGER` · `ADMIN`
- `AddressType`: `HOME` · `WORK` · `OTHER`
- `DiscountType`: `PERCENTAGE` · `FIXED_AMOUNT`
- `OrderStatus`: `PENDING` · `PAID` · `SHIPPED` · `DELIVERED` · `CANCELLED`
- `PaymentStatus`: `PENDING` · `COMPLETED` · `FAILED` · `REFUNDED`
- `PaymentMethod`: `CARD` · `PAYPAL` · `COD`
- `ShippingStatus`: `PENDING` · `SHIPPED` · `DELIVERED` · `RETURNED`

**Auth / account**
```jsonc
RegisterRequest  { "email": "a@x.com", "password": "min8chars", "firstName": "Alice", "lastName": "A", "phone": null }
LoginRequest     { "email": "a@x.com", "password": "min8chars" }
TokenResponse    { "accessToken": "ey...", "refreshToken": "ey...", "tokenType": "Bearer", "expiresIn": 900 }
UserResponse     { "id": 7, "email": "a@x.com", "firstName": "Alice", "lastName": "A", "phone": null, "roles": ["CUSTOMER"], "mustChangePassword": false }
UpdateProfileRequest { "firstName": "Alice", "lastName": "A", "phone": "555-0100" }
ChangePasswordRequest { "currentPassword": "min8chars", "newPassword": "min8chars" }
AddressRequest   { "line1": "1 Trail Rd", "line2": null, "city": "Boulder", "state": "CO", "postalCode": "80301", "country": "USA", "type": "HOME", "isDefault": true }
AddressResponse  { "id": 1, "line1": "...", "line2": null, "city": "...", "state": "...", "postalCode": "...", "country": "...", "type": "HOME", "isDefault": true }
```
*Validation:* email must be valid; password 8–100 chars; address line1/city/postalCode/country/type required. Note the JSON key is **`isDefault`**.

**Admin — users & staff**
```jsonc
CreateStaffRequest      { "email": "mara@adventuredose.com", "password": "TempPass123!", "firstName": "Mara", "lastName": "Manager", "phone": null }
UpdateUserStatusRequest { "enabled": false }   // false = ban, true = un-ban
AdminUserResponse       { "id": 8, "email": "mara@adventuredose.com", "firstName": "Mara", "lastName": "Manager", "phone": null, "roles": ["MANAGER"], "enabled": true, "mustChangePassword": true, "createdAt": "..." }
```
*Validation:* `CreateStaffRequest` email valid + password 8–100; `UpdateUserStatusRequest.enabled` required. `mustChangePassword` is `true` right after provisioning and clears once the user changes their password.

**Catalog**
```jsonc
CategoryRequest  { "parentId": null, "name": "Helmets", "slug": "helmets", "description": null, "imageUrl": null, "displayOrder": 1 }
CategoryResponse { "id": 1, "parentId": null, "name": "Helmets", "slug": "helmets", "description": null, "imageUrl": null, "displayOrder": 1, "active": true, "children": [ /* CategoryResponse */ ] }
ProductRequest   { "categoryId": 1, "name": "Trail Helmet", "slug": "trail-helmet", "description": null, "basePrice": 120.00, "brand": "AdventureDose" }
ProductResponse  { "id": 5, "categoryId": 1, "name": "...", "slug": "...", "description": null, "basePrice": 120.00, "brand": "...", "active": true, "createdAt": "..." }
ProductVariantRequest  { "sku": "TH-RED-M", "size": "M", "color": "Red", "priceOverride": null, "stockQuantity": 10, "restockLevel": 3 }
ProductVariantResponse { "id": 3, "productId": 5, "sku": "TH-RED-M", "size": "M", "color": "Red", "priceOverride": null, "listPrice": 120.00, "effectivePrice": 108.00, "stockQuantity": 10, "restockLevel": 3, "active": true }
VariantPriceStockResponse { "variantId": 3, "productId": 5, "sku": "TH-RED-M", "productName": "Trail Helmet", "effectivePrice": 108.00, "stockQuantity": 10, "available": true }
ProductImageRequest  { "variantId": null, "url": "https://cdn/x.jpg", "altText": "Front", "displayOrder": 1, "primary": true }
ProductImageResponse { "id": 1, "productId": 5, "variantId": null, "url": "...", "altText": "Front", "displayOrder": 1, "primary": true }
```
> `listPrice` = pre-discount price; `effectivePrice` = after the best active discount. Show a strikethrough when they differ. `rating` validations: `ProductVariantRequest.stockQuantity` ≥ 0, required.

**Reviews / wishlist**
```jsonc
ReviewRequest         { "rating": 5, "text": "Great" }      // rating 1..5
ReviewResponse        { "id": 1, "productId": 5, "userId": 7, "rating": 5, "text": "Great", "createdAt": "..." }
ReviewSummaryResponse { "productId": 5, "averageRating": 4.5, "reviewCount": 12 }
WishlistResponse      { "userId": 7, "items": [ { "id": 1, "productVariantId": 3, "addedAt": "..." } ] }
```

**Coupons / discounts**
```jsonc
CouponRequest    { "code": "SAVE20", "discountType": "PERCENTAGE", "discountValue": 20, "minSpend": 50, "maxUses": 100, "perUserLimit": 1, "validFrom": "2026-01-01T00:00:00Z", "validUntil": "2026-12-31T23:59:59Z", "active": true }
CouponResponse   { "id": 1, "code": "SAVE20", "discountType": "PERCENTAGE", "discountValue": 20, "minSpend": 50, "maxUses": 100, "usedCount": 3, "perUserLimit": 1, "validFrom": "...", "validUntil": "...", "active": true }
CouponValidationResponse { "valid": true, "code": "SAVE20", "discountType": "PERCENTAGE", "discountValue": 20, "discountAmount": 20.00, "message": "Valid" }
DiscountRequest  { "productId": 5, "categoryId": null, "type": "FIXED_AMOUNT", "value": 15, "validFrom": "...", "validTo": "...", "active": true }   // set EXACTLY ONE of productId / categoryId
DiscountResponse { "id": 1, "productId": 5, "categoryId": null, "type": "FIXED_AMOUNT", "value": 15, "validFrom": "...", "validTo": "...", "active": true, "createdBy": 1 }
```

**Cart / orders**
```jsonc
CartResponse     { "cartId": 1, "userId": 7, "items": [ { "id": 9, "productVariantId": 3, "productName": "Trail Helmet", "sku": "TH-RED-M", "unitPrice": 108.00, "quantity": 2, "lineTotal": 216.00, "available": true, "stockQuantity": 10 } ], "subtotal": 216.00 }
CheckoutRequest  { "shippingAddress": { "recipientName": "Alice", "line1": "1 Trail Rd", "line2": null, "city": "Boulder", "state": "CO", "postalCode": "80301", "country": "USA", "phone": "555-0100" }, "paymentMethod": "CARD", "couponCode": null, "shippingMethod": "Standard" }
OrderResponse    { "id": 1, "orderNumber": "AD-...", "userId": 7, "status": "PAID", "subtotal": 216.00, "discountAmount": 0.00, "total": 216.00, "couponCode": null, "shippingAddress": "{...json...}", "paymentStatus": "COMPLETED", "paymentMethod": "CARD", "transactionId": "TXN-...", "shippingStatus": "PENDING", "shippingMethod": "Standard", "trackingNumber": null, "items": [ { "id": 1, "productVariantId": 3, "productName": "Trail Helmet", "sku": "TH-RED-M", "unitPrice": 108.00, "quantity": 2, "lineTotal": 216.00 } ], "createdAt": "..." }
OrderSummaryResponse { "id": 1, "orderNumber": "AD-...", "userId": 7, "status": "PAID", "total": 216.00, "paymentStatus": "COMPLETED", "shippingStatus": "PENDING", "createdAt": "..." }
```
> `OrderResponse.shippingAddress` is a JSON **string** snapshot — `JSON.parse` it for display. Order line snapshots (`productName`, `sku`, `unitPrice`) are frozen at purchase time; don't re-fetch live catalog data for past orders.

**Analytics**
```jsonc
SalesAnalyticsResponse {
  "revenue": { "today": 0, "thisWeek": 0, "thisMonth": 350.00, "thisYear": 350.00, "allTime": 350.00, "monthOverMonthPct": 12.5 },
  "totalOrders": 5, "ordersByStatus": { "PAID": 1, "SHIPPED": 1, "DELIVERED": 1, "PENDING": 1, "CANCELLED": 1 },
  "averageOrderValue": 116.67, "totalDiscountsGiven": 20.00, "couponRedemptions": 1,
  "refunds": { "count": 1, "amount": 120.00 },
  "revenueByPaymentMethod": [ { "paymentMethod": "CARD", "orders": 2, "revenue": 300.00 } ],
  "operations": { "awaitingPayment": 1, "awaitingShipment": 1, "avgHoursToShip": 0.0 } }
RevenueTrendPoint   { "month": "2026-06", "revenue": 350.00 }
TopProductResponse  { "productVariantId": 3, "sku": "TH-RED-M", "productName": "Trail Helmet", "unitsSold": 3, "revenue": 300.00 }
TopCustomerResponse { "userId": 7, "orders": 2, "totalSpent": 300.00 }
CatalogAnalyticsResponse {
  "counts": { "products": 12, "variants": 30, "categories": 6 },
  "inventory": { "totalValue": 12000.00, "lowStock": 3, "outOfStock": 1 },
  "totalReviews": 40, "promotions": { "activeCoupons": 2, "activeDiscounts": 1 } }
LowStockItemResponse { "variantId": 3, "sku": "TH-RED-M", "stockQuantity": 2, "restockLevel": 5 }
TopRatedProductResponse { "productId": 5, "productName": "Trail Helmet", "averageRating": 4.5, "reviewCount": 12 }
MostWishlistedResponse  { "productVariantId": 3, "sku": "TH-RED-M", "wishlistCount": 8 }
CustomerAnalyticsResponse { "totalUsers": 120, "totalCustomers": 117, "newThisMonth": 14, "newThisYear": 120, "usersByRole": { "CUSTOMER": 117, "MANAGER": 2, "ADMIN": 1 } }
```

---

## 6. Behavioural notes for the frontend

- **Browsing is open** — the storefront (categories, products, variants, images, reviews, discounts via GET) needs no token. Show prices using `effectivePrice` (and strike through `listPrice` when lower).
- **Product detail page:** load the product, its variants (`/products/{id}/variants`), images (`/products/{id}/images`), reviews + summary, and applicable discounts. Use `/api/variants/{id}` for the authoritative purchasable price/stock when a variant is selected.
- **Cart is server-side and live-priced** — re-render from each `CartResponse`; don't compute totals client-side. Handle `available:false` lines gracefully.
- **Checkout:** preview the coupon (`/coupons/validate`) before placing the order; expect `409` if a line went out of stock between cart and checkout (show "out of stock, please adjust").
- **Auth UX:** on `401`, transparently refresh once and retry; if refresh fails, route to login. Gate admin/manager UI on the `roles` claim, but the backend is the real guard.
- **Realtime/async:** stock decrements and order→stock effects happen asynchronously (Kafka). Order status changes are staff-driven; poll `/api/orders/{id}` for updates (no WebSocket in v1).
- **Admin dashboard:** compose the dashboard from the §4.11 endpoints. Managers see sales + catalog; only admins see customer analytics — hide that card for managers and handle a `403` defensively.
- **Staff & accounts (admin only, §4.12):** an admin provisions managers (email + temp password), browses the user directory (the only customer list — filter `?role=CUSTOMER`), and bans/un-bans accounts. After login, check `mustChangePassword` on `UserResponse`/`GET /api/users/me`; if `true`, force the user to `PUT /api/users/me/password` before letting them work. A successful password change (and any ban) **revokes refresh tokens**, so the next silent refresh returns `401` — route to login when that happens rather than treating it as an error.
- **Money & dates:** amounts are 2-dp decimals; timestamps are ISO-8601 UTC (`Instant`). Format for display only.
