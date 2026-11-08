# `lib/` — backend data layer

Typed client for the Adventure Dose backend. Hand-written from [`API.md`](../../API.md).
**No UI here** — this is the data/contract layer the visual work will consume later.

## Usage

```ts
import { api, ApiError, type ProductResponse } from '@/lib/api';

// Public browse (no token)
const page = await api.products.list({ categoryId: 1, page: 0, size: 20 });
const product = await api.products.get(5);

// Authed (token comes from the configured TokenStore)
const cart = await api.cart.get();

// Errors are typed
try {
  await api.orders.checkout(req);
} catch (e) {
  if (e instanceof ApiError && e.isConflict) {
    // 409 — a line went out of stock
  }
}
```

## Layout

| Path | What |
|---|---|
| `api/config.ts` | Gateway base URL (`NEXT_PUBLIC_API_BASE_URL`, default `http://localhost:8080`) |
| `api/http.ts` | fetch transport: auth header, **401→refresh→retry**, JSON, `ApiError` |
| `api/errors.ts` | `ApiError` with `isValidation/isConflict/…` + `fieldErrors` |
| `api/query.ts` | query-string builder |
| `api/tokenStore.ts` | pluggable token storage (in-memory default; swap in the auth phase) |
| `api/endpoints/*` | one module per resource |
| `api/index.ts` | `api` façade + re-exports |
| `types/*` | DTOs & enums by domain |
| `utils/money.ts` | `formatMoney` (display only) |

## Conventions

- **Auth:** authed calls attach `Authorization: Bearer`; a 401 triggers one transparent
  refresh + retry. Tokens are read/written through `TokenStore` — call
  `configureTokenStore()` once the auth UI exists. `api.auth.login/refresh` do **not**
  persist tokens themselves (that's the session layer's job, built later).
- **Money:** server-computed; never do client-side money arithmetic. `formatMoney` is
  display-only.
- **Pagination:** list endpoints take `{ page, size, sort }` and return `Page<T>`.

## Coverage — full API surface (phases 1 + 2)

Every endpoint in `API.md` §4 is wrapped:

- **Storefront:** auth · users/me + addresses · categories · products · variants ·
  images · reviews · discounts · coupon validate · cart · orders/checkout · wishlist.
- **Staff/admin:** category/product/variant/image CRUD · coupon CRUD · discount CRUD ·
  order management (`listAll`/`updateStatus`/`updateShipping`) · analytics (sales,
  catalog, customers) · admin users/staff (`createStaff`/`listUsers`/`getUser`/
  `updateUserStatus`).

Auth scope per call is set in each endpoint module (`auth: false` for public reads).
Role enforcement is server-side — gate staff/admin UI on the JWT `roles` claim, but
expect and handle `403`/`404` defensively.
