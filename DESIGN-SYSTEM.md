# DESIGN-SYSTEM.md — Matessa (Frontend Execution)

> **For the design + frontend agent.** This is the authority for *how* the frontend is built — design tokens, global chrome, components, the mobile decisions, screens in scope, motion, the admin dashboard spec (incl. the India orders map). Brand identity (the *what/why* — palette meaning, voice, logo rules) lives in `BRAND.md`, which owns the color/type *values*. Translating the designed pages is governed by `FRONTEND-BUILD.md`. Data/API behavior: `ARCHITECTURE.md` + `api.md`. When code conflicts with this doc, this doc wins unless the human says otherwise.

---

## 1. How this design process works (read first)

1. The visual design already exists: **`design-source/`** holds the Claude Design handoff — page prototypes (`project/*.dc.html`; canonical landing is **`Matessa Landing v4.dc.html`**, v3 is superseded), interactive sections as React JSX (`project/src/*.jsx`), assets (`project/assets/`), and the `_ds/` token package. Treat it as read-only reference.
2. This doc turns that design into implementation rules. When a visual question isn't answered here, open the design file for that page — do not invent.
3. Tokens are defined ONCE in `frontend/app/globals.css` (CSS custom properties) mirroring §2. Components consume tokens/utilities — no hardcoded hexes, radii, or font names in components.

## 2. Design tokens (single source of truth)

Implement exactly these (from `design-source/project/_ds/tokens/*.css`):

```css
/* colors */
--color-green: #3E5F2D;        /* primary */
--color-lightgreen: #7A9D3E;
--color-darkgreen: #2F3B28;    /* chrome + headings */
--color-orange: #F26323;       /* single action/hover accent */
--color-orange-hover: #E85D1F;
--color-craemy: #EADBA2;
--color-yellow: #F3CB57;
--color-black: #121212;
--color-white: #F9F7F3;        /* page background */

/* semantic */
--surface-page: var(--color-white);
--surface-card: #FFFFFF;
--surface-dark: var(--color-darkgreen);
--surface-dark-hover: #4A5C40;
--text-heading: var(--color-darkgreen);
--text-body: #374151;
--text-muted: #6B7280;
--text-on-dark: var(--color-craemy);
--text-on-dark-secondary: #D1D5DB;
--text-price: var(--color-green);
--action-primary: var(--color-green);
--action-hover: var(--color-orange);
--accent-highlight: var(--color-yellow);
--border-subtle: #E5E7EB;
--border-card: #6B7280;
--border-on-dark: rgba(255, 255, 255, 0.10);

/* shape & elevation */
--radius: 0.625rem;
--radius-2xl: 1rem;            /* review cards */
--radius-card: 2rem;           /* product image tiles */
--radius-pill: 9999px;         /* commerce CTAs, badges */
--shadow-card: 0 10px 15px -3px rgb(0 0 0 / .1), 0 4px 6px -4px rgb(0 0 0 / .1);
--shadow-card-hover: 0 25px 50px -12px rgb(0 0 0 / .25);
--container-max: 80rem;        /* max-w-7xl */
--header-height: 5rem;         /* 3.5rem mobile */

/* type (fonts injected by next/font) */
--font-heading: /* Asul */;
--font-body:    /* Quicksand */;
```

Motion durations: 300ms hovers, 500ms image zooms, marquee 25s linear.

## 3. Global chrome (all storefront pages)

- **Announcement marquee** at the very top: dark-green bar, scrolling promo line (~25s, pauses on hover), bold keywords highlighted in `--accent-highlight`.
- **Header:** sticky, dark green; goes 50% translucent + `backdrop-blur` after ~8px of scroll. Cream logo mark centered; nav in cream Quicksand bold; cart icon with **orange pill badge** for count.
- **Nav items:** Shop · What is mate?! · Our Story · Contact Us (+ Home via logo). Category deep-links (Mate / Accessories) come from the categories API — never hardcoded.
- **Footer:** dark green, yellow section headings, gray-300 links with `hover:text-orange hover:pl-1`.

## 4. Global mobile decisions (locked)

- **The bottom TabBar is retired.** Do not build bottom navigation.
- Mobile nav = **hamburger (left side of the top bar) opening a left slide-in drawer** over a dimmed, blurred overlay. Contents top→bottom:
  1. Close button + logo row.
  2. Nav links (the §3 items + Track order, Wishlist, Search).
  3. **Pinned at the bottom — the auth block:**
     - Logged out → pill **"Sign up"** button (user icon + label) linking `/login?mode=register`; secondary "Sign in" text link.
     - Logged in → **Gravatar avatar** (SHA-256 of trimmed+lowercased account email → `https://gravatar.com/avatar/<hash>?d=identicon`) + display name, labeled **"My account"** → `/account`; staff additionally get **"Dashboard"** → `/admin`.
- Drawer is focus-trapped, closes on Esc/overlay tap, and slides with a 300–500ms ease.
- Everything else mobile-first: single-column sections, `--header-height` 3.5rem.

## 5. Component inventory (build once, reuse)

| Component | Spec |
|---|---|
| `Button` | pill, Quicksand bold, white text; primary green→orange hover; secondary `rounded-md` lightgreen→darkgreen |
| `AnnouncementBar` | §3 marquee |
| `SectionHeading` | Asul bold, 3xl/4xl, centered, darkgreen, Title Case |
| `ProductCard` | white card; image tile `--radius-card` on light-gray bg with `mix-blend-multiply`; thin gray border → orange on hover; `--shadow-card`→`--shadow-card-hover` + scale 1.05; name Asul xl; ₹ price in green (`formatMoney`); pill Add-to-Cart |
| `ReviewCard` | `--radius-2xl`, first-person Indian customer quotes, lightgreen stars |
| `BenefitCard` | lucide icon (Leaf/Zap/HeartPulse style) + short benefit copy |
| `QuoteBanner` | full-bleed photo, dark overlay `rgba(15,35,20,.55)`, parallax, Asul quote |
| `SiteHeader` / `SiteFooter` | §3 chrome |
| `MobileDrawer` | §4 |
| `Badge` | pill, yellow or orange fill per context |

Icons: lucide-style stroke icons (the repo's existing `components/ui/icons.tsx` pattern); no emoji.

## 6. Screens in scope

| Screen | Source | Notes |
|---|---|---|
| Landing `/` | `Matessa Landing v4.dc.html` | hero "The Ritual of …", Covering All Aspects, Our Blends (3 products from API), Why Drink Mate?, Better Than Tea (energy comparison), Who Drinks Mate? (footballer gallery), quote banner |
| Shop `/shop` | Product grid + design's card style | categories **from API** (ShopTabs): Mate, Accessories — seeded via dashboard |
| PDP `/product/[slug]` | `Product Page.dc.html` | pack shot, ingredients illustrations, reviews |
| What is mate?! `/what-is-mate` | `What is Mate - Scroll Reveal Preview.html` + `src/whatismate.jsx`, `what.is.mate.jsx`, `world-map.jsx`, `mateRitual.jsx`, `time-line-mate.jsx`, `indian-mate.jsx` | scroll-reveal storytelling page |
| Our Story `/about` | `Our Story.dc.html` + `src/quotes.jsx` | story banners |
| Contact `/contact` | `Contact Us.dc.html` | existing contact API |
| Cart / Checkout / Track / Login / Account / Orders / Wishlist / Search | translate the existing pages' *purpose* into the Matessa skin | API + `lib/api` types unchanged; checkout keeps the Indian-state dropdown |

## 7. Admin dashboard (staff-only, `/admin`)

Login already routes ADMIN/MANAGER → `/admin` (AuthForm). The admin shell keeps its dark theme, restyled with Matessa green accents.

**Layout per the `kpi-dashboard-design` skill** (installed at `.claude/.agents/skills/kpi-dashboard-design/` — follow it):
1. **Headline KPI row (4–6 cards, 5-second scan):** Revenue this month (+MoM trend arrow), Orders total, AOV, Awaiting shipment (alert-colored when > 0), Revenue today. Value big, label small, trend indicator with direction+color.
2. **Revenue trend** chart (12 months).
3. **Breakdowns:** orders by status (consistent status colors), revenue by payment method, top products / top customers tables.
4. **End of page — India orders map** (§8).

## 8. India orders map (admin, end of dashboard)

- Client component `IndiaOrdersMap` renders India with **visible state borders** (vendored TopoJSON of the 36 states/UTs at `frontend/lib/data/india-states.topo.json`, rendered via `d3-geo` + `topojson-client`).
- Data: `GET /api/analytics/sales/orders-by-state` (server-fetched with the other dashboard data). Join on the **canonical names** in `frontend/lib/constants/indianStates.ts` (checkout dropdown guarantees the match).
- Interaction: hover → tooltip `{State} — {n} orders`; click → pins the state in a side panel. Choropleth tint: green scale from `--color-white` → `--color-green` by count; zero-order states stay outlined only.
- Below the map: list any counts that didn't match a map region (legacy/"Other" states) so no data is silently dropped.

## 9. Currency (locked)

All money display goes through `formatMoney` (`frontend/lib/utils/money.ts`): **INR, `en-IN`, 0 decimals → ₹499**. No `$`/`₹` template literals in components. Backend keeps plain `BigDecimal` amounts — currency is a display concern.

## 10. Motion

Framer-motion (typed, client components only): fade+rise entrances with stagger; card spring hovers; image zoom 500ms; marquee 25s pause-on-hover; parallax quote banners; calm logo glow. Respect `prefers-reduced-motion` (disable marquee/parallax, keep opacity fades). Keep client boundaries small — motion wrappers, not whole pages.

**Landing hero pouch (diverges from `Matessa Landing v4.dc.html`).** The pouch stage in `components/home/HeroFlavorCycler.tsx` is no longer the flat photo cycler from the design:

- **First paint / fallback:** a two-sided CSS pouch built from the flat pack art in `public/brand/pack/` (front + back). Tap flips it; the flavor's ingredient cut-outs float around it.
- **3D (`components/home/hero/PouchScene.tsx`, three.js + @react-three/fiber, lazy-loaded, never server-rendered):** a real pouch that sways, drags round, flips on tap, and spins a full turn to change flavor (art swaps edge-on). Ingredient cut-outs orbit it on two tilted rings.
- **3D is skipped** under `prefers-reduced-motion`, data-saver, fewer than 4 CPU cores, under 3 GB device memory, or no WebGL2. Reduced motion also gets no autoplay and an instant flip.
- **Controls under the stage (both modes):** blend tabs and ingredient chips (one swipeable row on phones, wrapped on wider screens). Selecting a chip or a floating cut-out highlights that ingredient and shows a one-line taste note — no percentages, no health claims.
- **Data:** `components/home/hero/flavors.ts`. A flavor appears in the hero only when it has both `front` and `back` art; Raw Mate is waiting on its pack art.

## 11. Agent setup checklist

- [ ] Tokens in `frontend/app/globals.css` mirror §2 exactly.
- [ ] Asul + Quicksand via `next/font/google` in `app/layout.tsx` → `--font-heading`/`--font-body`.
- [ ] Brand assets served from `frontend/public/brand/` (logos, patterns, product shots).
- [ ] `design-source/` never imported by app code — reference only.
- [ ] Old Adventure Dose styling (`adventure.css`, wing motifs, $ prices) is removed page-by-page as each screen is translated; no new code may depend on it.
- [ ] Categories/products only via API — seed **Mate** + **Accessories** and the 3 blends through the dashboard/admin API, never in code.
