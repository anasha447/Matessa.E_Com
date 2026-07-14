# FRONTEND-BUILD.md — Adventure Dose (Design → Next.js translation)

> **For the frontend agent.** This doc governs *translating the existing Claude Design output (HTML + Tailwind) into the production Next.js app*. It covers process, conventions, and the file/animation rules for that translation. It does **not** restate visual identity or design system — those are authoritative in `BRAND.md` and `DESIGN-SYSTEM.md`; read those for all styling/layout/token decisions. Data/API behavior: `ARCHITECTURE.md`, `DATA-MODEL.md`, and the provided `api.md`. When code conflicts with this doc, this doc wins unless the human says otherwise.

---

## 1. What we're doing

A frontend was designed in Claude Design as **HTML + Tailwind utility classes** (with the brand already applied). The job is to translate it into the **already-initialized Next.js + TypeScript + Tailwind** project — **faithful to the design but idiomatic React** (proper components, not a literal HTML dump).

- **Scope of this phase: UI only.** Build the screens/components to match the design. Wire data later — use the provided `api.md` to shape props/types and stub data now, but do not implement real network calls unless told.
- **Faithful + idiomatic:** match the design's layout, spacing, and look; express it through reusable, typed React components and Next.js conventions. Improve structure where React clearly allows (componentization, props) without changing the visual result.

---

## 2. How the agent gets the design (READ — this is the setup)

A Claude Code agent **cannot browse a Claude Design workspace URL** and pull files. It works against files in the repo. So:

1. **Export the design source into the repo.** The human exports the HTML/CSS (and any assets) from Claude Design into a folder, e.g. `/design-source/` (one subfolder or file per page: `home.html`, etc.). The agent reads these as the translation source.
2. The workspace **link is for the human** (to view/verify and copy markup), not a source the agent can fetch.
3. **Fallback if export is messy:** the human pastes the page markup directly into the task, page by page, and the agent translates from that.
4. Treat `/design-source/` as **read-only reference** — never ship it; it stays out of the app build (and can be `.gitignore`d or kept in a non-bundled folder). The real app lives under the Next.js `app/` structure.

> Before translating a page, confirm its source HTML is available in `/design-source/` (or pasted). If a page's source is missing, ask for it — do not invent the layout.

---

## 3. Hard translation rules

- **TypeScript only — no loose JS.** No standalone `.js` files, no `<script>` tags, no hand-rolled DOM manipulation (`document.querySelector`, manual `addEventListener`). All behavior lives in typed `.tsx`/`.ts` React components. (Note: TS compiles to JS and React interactivity runs as JS in the browser — that's expected; the rule is "React + TS patterns, not vanilla JS files.")
- **Tailwind stays Tailwind.** The design already uses Tailwind utilities — carry them over directly. Do not rewrite into plain CSS. Use the project's existing Tailwind config; reference brand tokens from there (per `DESIGN-SYSTEM.md`) rather than hard-coded hex/px where a token exists.
- **Componentize, don't dump.** Repeated markup (cards, buttons, nav) → reusable typed components with props. A page is composed of components, not one giant block of JSX.
- **Type everything.** Props via `interface`/`type`; no `any`. Where component data mirrors backend shapes, derive the types from `api.md`/`DATA-MODEL.md`.
- **Next.js App Router conventions.** Pages under `app/`. Default to **Server Components**; add `"use client"` only to components that need interactivity/state/browser APIs (e.g. the hero animation, interactive cards, anything with `useState`/`useEffect`/scroll hooks). Keep client boundaries as small as possible.
- **`next/image`** for images, **`next/link`** for navigation, **`next/font`** for fonts (Magnetar setup per `DESIGN-SYSTEM.md`).
- **Semantic, accessible markup.** Proper landmarks/headings/labels/alt text; visible focus states; keyboard operability — preserve or improve on the design.
- **Match, don't redecorate.** Don't introduce new colors, radii, fonts, or layout patterns beyond what the design + `BRAND.md`/`DESIGN-SYSTEM.md` specify.

---

## 4. Animation / interactivity (incl. the hero)

The design's motion (e.g. the hero scroll-swap) must be rebuilt as **typed React**, not ported as vanilla JS.

- **Approach:** implement scroll/parallax/reveal with a React-friendly, TS-typed library (e.g. **Framer Motion** `useScroll`/`useTransform`, or GSAP via a typed wrapper) inside a `"use client"` component. No `<script>` tags, no manual scroll listeners.
- **Hero helmet-swap:** build per `DESIGN-SYSTEM.md §8` (the mechanics, asset requirements, and graceful-degradation/`prefers-reduced-motion` rules are defined there — follow them; this doc does not restate them).
- **Motion posture:** subtle and fast, per `DESIGN-SYSTEM.md §7`. Always honor `prefers-reduced-motion`.
- Pick the implementation library yourself (idiomatic, well-typed, tree-shakeable); state the choice in the plan.

---

## 5. Suggested structure (within the existing app/)

Mirror the design's pages; keep components token-based and reusable:
```
app/
  layout.tsx            // fonts, theme provider, global shell
  page.tsx              // Home (first page to build)
  (routes…)             // other pages as designed
components/
  ui/                   // primitives: Button, Card, Badge, Input, QtyStepper…
  layout/               // Nav (BottomTabBar/TopNav), Footer, SectionHeading
  hero/                 // the helmet-swap hero (client component)
  product/              // ProductCard, VariantSelector, PriceTag…
lib/
  types.ts              // shared types (derived from api.md / DATA-MODEL.md)
  (api stubs later)
```
Build the **primitives in `components/ui/` first**, then assemble pages from them — so the whole app is on-brand and consistent by default. (Folder names illustrative; match the project's conventions.)

---

## 6. Process & order

1. Confirm the design source for the target page is in `/design-source/` (or pasted).
2. **Start with the Home page** (including the hero), then proceed page by page.
3. Per page: extract reusable pieces into `components/` first, then compose the page.
4. **Plan-first:** before writing code for a page, present a short plan — which components you'll extract, client vs server split, animation library choice, and any asset needs — and wait for approval.
5. UI only this phase; use `api.md` to type props and stub data, no real calls unless instructed.
6. Verify against the design visually and against `BRAND.md`/`DESIGN-SYSTEM.md` for tokens, plus `prefers-reduced-motion` and basic a11y, before considering a page done.

---

## 7. Assets the human provides
- `/design-source/` — exported HTML/Tailwind per page (or pasted markup).
- Hero assets per `DESIGN-SYSTEM.md §8` (man photo + angle-matched transparent helmet PNGs + per-helmet palette color).
- Magnetar font files (self-hosted) per `DESIGN-SYSTEM.md`.
- Logo + wing-motif SVG, and any other brand imagery.
- `api.md` for data shapes/types.

> Styling, tokens, layout, motion specs, and the hero mechanics are NOT in this file by design — they live in `BRAND.md` and `DESIGN-SYSTEM.md`. This file is only the *translation process and code conventions*.
