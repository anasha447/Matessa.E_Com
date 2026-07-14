> **NOTE (Matessa):** This document still describes the Adventure Dose brand identity. It is kept as reference until the Matessa brand is defined — do not apply its brand-specific content (name, palette meaning, voice, logo) to new Matessa-facing work.

# DESIGN-SYSTEM.md — Adventure Dose (Frontend Execution)

> **For the design + frontend agent.** This is the authority for *how* the frontend is built — design tokens, components, layout, the mobile→desktop handoff, motion, the hero animation, and frontend innovations. It is the **how**; brand identity (the *what/why* — palette meaning, voice, logo rules) lives in `BRAND.md`, which is the source of truth for color/type *values*. Read both together. Data/API behavior: `ARCHITECTURE.md` + `DATA-MODEL.md`. When code conflicts with this doc, this doc wins unless the human says otherwise.

---

## 1. How this design process works (read first)

1. **Mobile is designed first and is the source of truth.** Every screen is decided at mobile width before any desktop layout exists. The human provides mobile screenshots; those are authoritative for layout & hierarchy.
2. **Desktop is *derived* from mobile, not designed independently** (rules in §6). Same components, hierarchy, and brand — more horizontal space. No new patterns that weren't established on mobile.
3. **Screenshots define layout; `BRAND.md` defines look.** Match a screenshot's structure (element order, grouping, what's primary); apply brand tokens for all styling.
4. **Reference brand:** The Solid Store is a **structural/layout reference only** (grid density, card anatomy, catalogue organization, nav patterns) — never a visual source. Adventure Dose's identity always applies.

---

## 2. Design tokens (the single source of truth)

Translate `BRAND.md` values into tokens defined **once** in `globals.css` (CSS custom properties) and mirrored in `tailwind.config.ts`. **Never hard-code hex/spacing in components — reference tokens only.** `BRAND.md` is the source of truth for color/type values; this section is their executable form.

### Core + state colors
```
--accent:        #FF6B1A   /* Hi-Vis Orange — primary action, price, energy */
--accent-hover:  #E65A0E   /* pressed/hover on primary */
--accent-tint:   #FFE7D6   /* subtle highlight fills on light */
--on-accent:     #3A1500   /* text/icons ON orange (never pure black/white) */
--cool:          #2E9CC4   /* Dusk Sky — functional accent: links, info */
--success:       #3F8F5B   /* in stock, confirmations */
--warning:       #E0A92E   /* low stock, cautions */
--danger:        #D24B3E   /* errors, out of stock */
```

### Dual-canvas neutral sets (light = default build target)
```
LIGHT (default)                         DARK ("dusk")
--bg-page:        #EEF1F4               --bg-page:        #14171C
--bg-surface:     #FFFFFF               --bg-surface:     #1C2128
--bg-raised:      #F4F6F8               --bg-raised:      #242B33
--text-primary:   #14171C               --text-primary:   #EEF1F4
--text-secondary: #5A6675               --text-secondary: #9AA7B5
--border:         rgba(20,23,28,.12)    --border:         rgba(238,241,244,.14)
```
Orange/cool/state tokens are constant across both modes; only neutrals invert. Implement via `[data-theme="dark"]` (or `prefers-color-scheme`). Verify every text/bg pairing passes WCAG AA in BOTH modes.

### Shape, spacing, elevation
```
--radius:     6px            /* sharp & technical — cards, inputs, buttons all 6px; no pills */
--space:      4/8/12/16/24/32/48/64   /* use multiples; don't freehand */
elevation:    mostly FLAT — prefer 1px --border over shadows.
              One subtle shadow token only for overlays (modals/dropdowns). No stacked glows.
```

### Type tokens & loading
```
--font-display: Magnetar      /* self-hosted via next/font/local */
--font-body:    Barlow        /* next/font/google */
--font-mono:    JetBrains Mono /* next/font/google */
```
- **Magnetar (self-hosted):** human provides files → `frontend/public/fonts/magnetar/` (woff2+woff). Load with `next/font/local` in `app/fonts.ts`, expose `--font-display`. If files are missing at build time, ASK the human and temporarily fall back to a condensed geometric sans (Saira/Oswald) clearly marked as placeholder — never silently ship the wrong font.
- **Type scale:** H1 40–56 / H2 28–34 / H3 20–24 / body 16 / small 14 / caption 12 (px). Line-height ~1.6 body, ~1.05–1.1 display. Headings UPPERCASE Magnetar tracked +1–3px; body sentence-case Barlow; mono for SKUs/spec rows only.

---

## 3. Global mobile decisions (locked)

- **Target width:** design at 390px; support 360–430px gracefully.
- **Primary nav: bottom tab bar** — app-like, thumb-friendly, persistent. Tabs (proposed; confirm vs screenshots): **Home · Shop · Search · Bag · Account**. Active tab = `--accent`; inactive = `--text-secondary`. Bag shows a count badge.
- **Catalogue grid: 2-up** (denser, more product per screen).
- **Top bar:** minimal (logo/section title + optional filter/wishlist action); search has its own tab.
- **Default canvas: light**; dark "dusk" supported.
- **Radius 6px, flat elevation, borders over shadows.**
- **Touch targets ≥ 44px;** primary actions thumb-reachable (lower half where possible).

---

## 4. Component inventory (built once, used across breakpoints)

Build these token-based primitives BEFORE assembling screens; each works mobile-first and expands per §6:
- **ProductCard** (2-up mobile → N-up desktop) — image (4:5 or 1:1), name (Magnetar small-caps), price (Magnetar bold, `--accent`), badge (stock/new), quick-add affordance.
- **BottomTabBar** (mobile) ↔ **TopNavBar** (desktop) — same destinations.
- **FilterSheet** (mobile) ↔ **FilterRail** (desktop) — same options/logic.
- **Button** (primary/secondary), **Badge**, **Input/Select**, **QtyStepper**, **PriceTag**, **VariantSelector**, **OrderSummary**, **SectionHeading** (optional faint wing watermark).

---

## 5. Screens in scope (7) + spec slots

Each screen: **purpose**, **mobile layout** (filled from screenshot), **desktop derivation**. `⟨AWAITING SCREENSHOT⟩` = provide the mobile screenshot before specifying; do NOT invent it.

### 5.1 Home / Landing
- Purpose: set tone, surface featured products/categories, drive into catalogue. Hosts the helmet-swap hero (§8).
- Mobile layout: ⟨AWAITING SCREENSHOT⟩
- Desktop: hero full-bleed wider; featured rows multi-column; bottom tabs → top nav.

### 5.2 Catalogue / listing
- Purpose: fast browse/scan — the core shopping surface. Grid 2-up.
- Filter/sort entry: ⟨AWAITING SCREENSHOT⟩
- Mobile layout: ⟨AWAITING SCREENSHOT⟩
- Desktop: 2-up → 3/4-up; persistent left filter rail; card unchanged.

### 5.3 Product detail (PDP)
- Purpose: product + adventure story, variants, price, add-to-bag. Candidate for spec-sheet treatment (§9).
- Variant selection: ⟨AWAITING SCREENSHOT⟩
- Mobile layout: ⟨AWAITING SCREENSHOT⟩
- Desktop: gallery left / details right; sticky add-to-bag.

### 5.4 Cart / Bag
- Purpose: review items, qty, totals/coupon, proceed.
- Mobile layout: ⟨AWAITING SCREENSHOT⟩
- Desktop: line items left / sticky order-summary right.

### 5.5 Checkout
- Purpose: complete purchase. Voice stays plain/clear. Candidate for trail-marker stepper (§9, later).
- Steps (single vs multi): ⟨AWAITING SCREENSHOT⟩
- Mobile layout: ⟨AWAITING SCREENSHOT⟩
- Desktop: form left / summary right; cap form width.

### 5.6 Search / Filter
- Purpose: query + narrow by category/price/size/color. Own tab.
- Mobile layout: ⟨AWAITING SCREENSHOT⟩
- Desktop: search top; filters → persistent rail (shared with catalogue).

### 5.7 Account / Orders
- Purpose: profile, addresses, order history + status.
- Mobile layout: ⟨AWAITING SCREENSHOT⟩
- Desktop: left menu + content pane.

---

## 6. Mobile → Desktop derivation rules

Derive desktop from the locked mobile screens; do NOT redesign.
- **Breakpoints:** mobile ≤640 (base, designed), tablet 641–1024, desktop ≥1025. Mobile-first CSS (base = mobile; layer up with `min-width`).
- **Nav transform:** mobile **bottom tabs → desktop top nav bar** (same destinations/labels/active-orange). Bag/account top-right. No bottom tabs on desktop.
- **Grid:** 2-up → tablet 3-up → desktop 4-up. Same card; only column count changes.
- **Filters:** mobile sheet → desktop persistent left rail. Same options/logic.
- **Single→two column:** PDP/cart/checkout go stacked → content + sticky summary/detail. Mobile stacking order defines the desktop primary column order.
- **Constraints:** cap container ~1200–1280px; narrower measure for text/forms (don't run full-width). Whitespace scales; density doesn't balloon.
- **No new components on desktop.** If desktop seems to need a pattern mobile lacked, update the mobile design + this doc first — don't invent in code.

---

## 7. Motion — subtle & fast

Default posture: present but quick and purposeful. Solid, not bouncy. No slow fades, no spring overshoot, no decorative loops.
- **Timing:** 150–250ms UI feedback, ease-out. Scroll/parallax tied to scroll position, not timers.
- **Wing as loader/transition:** replace generic spinners with the logo wings sweeping in; fast wing wipe for route transitions.
- **Scroll reveals:** content fades/translates up subtly on enter (short distance, fast) — key sections only, not everything.
- **Hover:** crisp, immediate (color shift, 1–2px lift). No mushy easing.
- **`prefers-reduced-motion` is mandatory** — provide a no-motion path that shows final state.

---

## 8. Hero section — the helmet-swap (v1 hero feature)

**Concept:** a head-on photo of a man; on scroll the current helmet lifts up/out while a new helmet drops down onto his head; the background gradient shifts color per helmet.

**Technique:**
- Layered composition: man's photo = fixed base; helmets = separate transparent PNG layers above the head; gradient = layer behind.
- Scroll-linked (Framer Motion `useScroll`+`useTransform`, or GSAP ScrollTrigger): map hero scroll progress to (a) helmet A translateY/opacity out (up), (b) helmet B translateY/opacity in (from below onto head), (c) gradient interpolation between two palette colors per helmet.
- **v1 scope = tier 1: 2D layer swap.** Do NOT attempt 3D head tracking or computed "worn" shadows.
- Gradient colors come from the palette (orange ↔ dusk sky ↔ steel washes); each helmet maps to one.
- Degrade gracefully: `prefers-reduced-motion` → man + one helmet, no scroll anim. Mobile → simpler swap or tap-through (long scroll-pinned heroes are awkward on small screens).

**Assets the human must provide (agent asks explicitly):**
1. One head-on photo of the man — neutral pose, consistent lighting, plain/removable background, ideally bareheaded (or helmet to be masked). **Head position is the anchor — must stay fixed.**
2. Each helmet as a transparent PNG, background removed, **same angle and scale as the head** so each lands in the same spot. Cross-PNG alignment is the #1 quality factor.
3. A palette color per helmet (for the gradient).

**Fallback if aligned photos aren't available:** swap helmets onto a head **silhouette/icon** instead of a real photo — zero alignment risk, still demonstrates the swap. Offer this rather than ship a misaligned composite.

---

## 9. Frontend innovations (roadmap)

Hero helmet-swap is the v1 hero feature (§8). Build 2–3 total for v1, not all:
- `[v1 candidate]` **Spec-sheet PDP** — product details as a technical data sheet (mono rows, labeled specs, a stamp). Differentiates from generic e-commerce.
- `[v1 candidate]` **Wing loader / route transitions** (see §7).
- `[later]` **360° helmet spin** (image-sequence first, not real 3D).
- `[later]` **Build-your-kit bundler** — helmet → matched gear/art, assembled like packing a bag.
- `[later]` **Trail-marker checkout stepper** — checkout steps re-skinned as waypoints.
- `[later]` **Day/dusk theme toggle** themed as a sun/dusk switch (not a generic moon).

---

## 10. Signature pattern — the wing motif (usage)

The wing-feather from the logo is the primary brand texture in UI.
- Extract a single wing/feather stroke as a reusable SVG.
- **Use:** section dividers, empty/loading states, faint watermark behind hero/section headings, card hover accents, footer.
- **Discipline:** low opacity (5–12% light, 8–16% dark); one wing accent per viewport section, not wallpaper. Monochrome (ink/steel) so orange stays loudest; an orange wing only for a single high-energy moment.
- Secondary textures (rare): thin diagonal speed lines for energy moments; faint grid only in technical/spec contexts.

---

## 11. Agent setup checklist (where things go)
- `frontend/app/globals.css` — all CSS custom properties, both token sets (light default + dark). Single source of truth for color/spacing/radius.
- `frontend/tailwind.config.ts` — map tokens to Tailwind theme keys (colors, fontFamily, borderRadius, spacing).
- `frontend/app/fonts.ts` — `next/font/local` Magnetar (`--font-display`); `next/font/google` Barlow (`--font-body`) + JetBrains Mono (`--font-mono`).
- `frontend/public/fonts/magnetar/` — self-hosted Magnetar (human provides).
- `frontend/public/brand/` — wing-motif SVG(s), logo files, hero photo + helmet PNGs (human provides hero assets).
- Build token-based primitives (Button/Card/Badge/Input) BEFORE any page.
- Before building the hero (§8) or anything needing photos/fonts, confirm the human has provided assets; ask if missing rather than improvising.

---

## 12. Adding a screenshot to this doc (process)
When the human provides a mobile screenshot:
1. Replace that screen's `⟨AWAITING SCREENSHOT⟩` with a concrete spec: element order top→bottom, grouping, primary/secondary, spacing rhythm, components used.
2. Note screen-specific interactions (sticky elements, sheets, expand/collapse).
3. Flag anything conflicting with `BRAND.md` (a radius/color that isn't ours) — apply the brand value, note the substitution.
4. Don't alter §3 global decisions without the human's say-so.

## 13. Open items to confirm against screenshots
- Final bottom-tab labels & icons (proposed: Home/Shop/Search/Bag/Account).
- Product-card anatomy (badge placement, quick-add affordance).
- Filter entry pattern (sticky bar vs button vs tab).
- PDP variant selection UI (chips vs dropdown).
- Checkout: single-page vs multi-step.
