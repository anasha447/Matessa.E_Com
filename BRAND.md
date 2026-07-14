> **NOTE (Matessa):** This document still describes the Adventure Dose brand identity. It is kept as reference until the Matessa brand is defined — do not apply its brand-specific content (name, palette meaning, voice, logo) to new Matessa-facing work.

# BRAND.md — Adventure Dose

> **For the frontend agent.** This is the authority for visual design, voice, and frontend UX of the Next.js app. Read it before building or styling any UI. When code conflicts with this doc, this doc wins unless the human says otherwise. Pairs with `ARCHITECTURE.md` (how the frontend talks to the backend) and `CLAUDE.md` (project rules).
>
> **Where tokens live:** the agent should translate the values below into a single source of truth — CSS custom properties in `globals.css` and matching keys in `tailwind.config.ts`. Define them ONCE there; never hard-code hex values in components. Components reference tokens only.

---

## 1. Brand essence

Adventure Dose sells helmets, adventure apparel, and wearable art — each piece tied to an adventure story. The brand exists to make the customer feel **confident and adventurous** the moment they see an ad or start browsing. Every screen should feel like the start of a ride, not a checkout form.

- **Personality:** adventurous, active, confident, technical-but-human.
- **Feeling to evoke:** readiness, momentum, capability — "gear up and go."
- **Visual energy:** high-vis safety-gear orange punching out of cool ink/steel. Sharp, technical, fast.

---

## 2. Color — palette "High-Vis Dusk"

Orange is the **accent and energy**, never the wallpaper. Rough budget: **60% neutral (ink/fog/steel), 30% structural, 10% orange** for actions, price, highlights.

### Core values
| Role | Name | Hex |
|---|---|---|
| Primary accent | Hi-Vis Orange | `#FF6B1A` |
| Ink (darkest neutral) | Ink | `#14171C` |
| Steel (mid structural) | Steel | `#33414F` |
| Functional cool accent | Dusk Sky | `#2E9CC4` |
| Light surface | Fog | `#EEF1F4` |

### Supporting / derived (for states & depth)
| Role | Hex | Notes |
|---|---|---|
| Orange hover/darker | `#E65A0E` | pressed/hover on primary actions |
| Orange tint (light bg wash) | `#FFE7D6` | subtle highlight fills |
| On-orange text | `#3A1500` | dark brown-black — text/icons ON orange (never pure black/white) |
| Success | `#3F8F5B` | "in stock", confirmations |
| Warning | `#E0A92E` | low stock, cautions |
| Danger | `#D24B3E` | errors, out of stock |
| Text on dark | `#EEF1F4` | body text on ink |
| Muted text on dark | `#9AA7B5` | secondary text on ink |
| Text on light | `#14171C` | body text on fog/white |
| Muted text on light | `#5A6675` | secondary text on light |

### Dual-canvas token sets
**Default canvas = LIGHT (fog/white-first).** Dark ("dusk") is the toggle. Define both as token sets; the agent builds and verifies LIGHT first, then dark.

```
LIGHT (default)                         DARK ("dusk")
--bg-page:        #EEF1F4               --bg-page:        #14171C
--bg-surface:     #FFFFFF               --bg-surface:     #1C2128
--bg-raised:      #F4F6F8               --bg-raised:      #242B33
--text-primary:   #14171C               --text-primary:   #EEF1F4
--text-secondary: #5A6675               --text-secondary: #9AA7B5
--border:         rgba(20,23,28,.12)    --border:         rgba(238,241,244,.14)
--accent:         #FF6B1A  (both)        --accent:         #FF6B1A (both — orange is constant)
--accent-hover:   #E65A0E  (both)
--cool:           #2E9CC4  (both)
```
**Rule:** orange stays the same hex in both modes (it's the brand constant). Neutrals invert. Every text/background pairing must pass WCAG AA — verify, the warm-on-cool combos are easy to get wrong.

---

## 3. Typography

Three typefaces, each with one job. Two weights of focus: regular (400) and bold (600/700 for display).

| Role | Font | Loading | Usage |
|---|---|---|---|
| Display / headings | **Magnetar** | **self-hosted** `@font-face` (license held) | H1–H3, hero, big labels, CTAs. UPPERCASE for impact, tracked +1–3px. |
| Body / UI | **Barlow** | Google Fonts | all reading text, inputs, nav, descriptions. Sentence case. Pairs with Magnetar's geometric/tech character via its slightly squared, athletic humanist shapes. |
| Numeric / spec accent | **JetBrains Mono** | Google Fonts | SKUs, order IDs, spec-sheet rows, sizing tables. Reinforces the technical gear-catalog feel. Use sparingly. |

### Self-hosting Magnetar (agent setup)
- Place font files under `frontend/public/fonts/magnetar/` (woff2 + woff).
- Use `next/font/local` to load Magnetar (avoids layout shift, self-hosted): define it in `app/fonts.ts` and expose via a CSS variable `--font-display`.
- Load Barlow + JetBrains Mono via `next/font/google`, exposed as `--font-body` and `--font-mono`.
- **The human must provide the Magnetar font files.** If absent at build time, agent should ask for them and temporarily fall back to a condensed geometric sans (e.g. Saira/Oswald) clearly marked as a placeholder — never silently ship the wrong font.

### Type rules
- Headings UPPERCASE in Magnetar; body sentence case in Barlow. Never all-caps body text.
- One display font, one body font — never add a fourth typeface for "variety."
- Type scale (suggested): H1 40–56px, H2 28–34px, H3 20–24px, body 16px, small 14px, caption 12px. Line-height ~1.6 body, ~1.05–1.1 display.

---

## 4. Shape, spacing, elevation

- **Corners: sharp & technical. Base radius `6px`.** Cards, inputs, buttons all `6px`. No pills, no soft 16px+ rounding. This is a brand signature — crisp, gear-like.
- **No rounded corners on single-sided border accents** (e.g. an orange `border-left` highlight stays square).
- **Spacing scale:** 4 / 8 / 12 / 16 / 24 / 32 / 48 / 64. Use multiples; don't freehand margins.
- **Elevation: mostly flat.** Rugged/technical reads solid, not floaty. Prefer 1px borders over shadows for separation. If a shadow is needed (modals, dropdowns), one subtle token only — never stacked glows.
- **Borders do the work:** `--border` token for dividers/cards. Crisp hairlines, not heavy rules.

---

## 5. Signature pattern — the wing motif

The **wing-feather motif from the logo** is the primary brand texture. It ties everything back to the mark without repeating the full logo.

- Extract a single wing/feather stroke (a few swept lines) as a reusable SVG asset.
- **Where to use it:** section dividers, empty states, loading states (see motion), behind hero/section headings as a faint watermark, card hover accents, the footer.
- **Discipline:** low opacity (5–12% on light, 8–16% on dark), never competing with content. It's seasoning. One wing accent per viewport section, not wallpaper.
- Keep it monochrome (ink/steel tint) so the orange stays the loudest thing on screen. Occasionally an orange wing for a single high-energy moment (hero, success state).
- Secondary supporting textures (use rarely): thin diagonal speed lines for motion/energy moments; a faint grid only on technical/spec contexts.

---

## 6. Motion — subtle & fast

Default posture: **motion is present but quick and purposeful.** Rugged/technical feels solid, not bouncy. No slow fades, no spring overshoot, no decorative loops.

- **Timing:** 150–250ms for UI feedback; ease-out. Scroll/parallax tied to scroll position, not timers.
- **The wing as loader/transition.** Replace generic spinners with the logo wings sweeping in. Page/route transitions can use a fast wing wipe.
- **Scroll reveals:** content fades/translates up subtly on enter (short distance, fast). Don't animate everything — key sections only.
- **Hover:** crisp, immediate (color shift, slight lift via border/translate 1–2px). No mushy easing.
- **Always respect `prefers-reduced-motion`** — provide a no-motion path that just shows final state. This is mandatory, not optional.

---

## 7. Hero section — the helmet-swap (v1 hero feature)

**Concept:** a photo of a man, head-on. On scroll, the current helmet lifts up and out while a new helmet design drops down onto his head; the background gradient shifts color with each helmet.

### How to build it (technique)
- Layered composition: the man's photo is a fixed base layer; helmets are separate transparent PNG layers stacked above the head; the background is a gradient layer behind everything.
- Scroll-linked animation (Framer Motion `useScroll` + `useTransform`, or GSAP ScrollTrigger): map scroll progress within the hero to (a) helmet A `translateY`/opacity out (upward), (b) helmet B `translateY`/opacity in (from below onto the head), (c) gradient color interpolation between two palette colors per helmet.
- **v1 scope = "tier 1": 2D layer swap.** Do NOT attempt 3D head tracking or making the helmet photorealistically "worn" with computed shadows — out of scope for v1.
- Gradient colors must come from the palette (orange ↔ dusk sky ↔ steel washes). Each helmet gets an associated palette color.
- Must degrade gracefully: `prefers-reduced-motion` → show the man with one helmet, no scroll animation. Mobile → simpler swap or a tap-through, since long scroll-pinned heroes are awkward on small screens.

### Assets the human must provide (agent should request these explicitly)
1. **One photo of the man** — shot head-on, neutral pose, consistent lighting, plain/removable background. Ideally bareheaded (or with a helmet that will be masked out) so helmets drop on cleanly. **The head position is the anchor — it must stay fixed.**
2. **Each helmet as a separate transparent PNG** (background removed), shot/rendered from the **same angle and same scale** as the head, so each lands in the same spot on the head. Alignment across PNGs is the single most important quality factor.
3. A palette color associated with each helmet (for the gradient shift).

### Fallback if aligned photos aren't available
If perfectly angle-matched photos can't be produced, swap helmets onto a **head silhouette/icon** instead of a real photo — less personal, zero alignment risk, still demonstrates the swap. Agent should offer this rather than ship a misaligned composite.

---

## 8. Voice & tone

**Confident and adventurous.** The customer should feel capable and ready — like every product comes with a story and an invitation to go do something. Speak like someone who's been on the trail and is handing you the gear, not a marketer.

- **Do:** short, punchy, active sentences. Lead with capability and story. "Gear that's been there." "Every piece has a story — go write yours." Confident, never arrogant.
- **Don't:** corporate filler ("elevate your journey," "synergy"), exclamation spam, over-promising, or hollow hype. Confidence comes from specifics, not volume.
- **Product copy:** tie each piece to its adventure story + what it does/survives. Spec facts in plain language ("abrasion-resistant shell, 3-season liner"), then the story hook.
- **Microcopy with character:**
  - empty cart → "Your kit's empty. Time to gear up."
  - 404 → "Wrong turn off the trail. Let's get you back."
  - order confirmed → "You're geared up. The adventure's on its way."
  - loading → wing sweeps + "Gearing up…"
- **One rule:** keep checkout, payment, and money/error states plain and clear. Be adventurous everywhere it builds excitement; be boringly clear everywhere a mistake costs the customer.

---

## 9. Imagery

- Real, gritty, in-use photography — gear on actual trails/roads, motion, golden-hour light. Never sterile studio shots for hero/lifestyle moments (studio-on-white is fine for the product-detail spec image only).
- Apply a **consistent treatment** (slightly cool-shadowed to match the ink/steel palette, raised contrast) so mixed supplier photos feel like one brand.
- Helmets are the hero product — give them the most cinematic treatment.
- Always provide `alt` text; never rely on an image alone to convey price/stock/action.

---

## 10. Frontend innovations (roadmap)

Hero helmet-swap is the v1 hero feature (§7). Other ideas, tagged by phase — build 2–3 total for v1, not all:

- `[v1 candidate]` **Spec-sheet PDP** — present product details like a technical data sheet (mono font rows, labeled specs, a stamp). Differentiates from generic e-commerce.
- `[v1 candidate]` **Wing loader / route transitions** — already part of motion (§6).
- `[later]` **360° helmet spin** (image-sequence first, not real 3D).
- `[later]` **Build-your-kit bundler** — helmet → matched gear/art, assembled like packing a bag.
- `[later]` **Trail-marker checkout stepper** — re-skin checkout steps as waypoints.
- `[later]` **Day/dusk theme toggle** themed as a sun/dusk switch (not a generic moon).

---

## 11. Hard brand rules (guardrails)

1. Orange is the accent, not the background. 60/30/10 neutral/structural/orange.
2. Tokens are the only source of color/spacing/type. Never hard-code hex in components — reference CSS variables / Tailwind tokens defined once in `globals.css` + `tailwind.config.ts`.
3. One display font (Magnetar), one body (Barlow), mono only for specs/SKUs. No fourth typeface.
4. Base radius 6px everywhere. Sharp, technical, consistent.
5. UPPERCASE Magnetar for headings/CTAs; sentence case Barlow for everything read.
6. Every text/bg pairing passes WCAG AA — verified in both light and dark.
7. Motion is fast, subtle, purposeful, and always has a `prefers-reduced-motion` path.
8. The logo gets clear space (≥ helmet height), never stretched, recolored outside palette, or placed on a busy photo without a scrim.
9. Light mode is the default build target; dark ("dusk") must work equally.
10. Consistency over cleverness — one radius, one elevation treatment, one spacing scale, one photo treatment.

---

## 12. Agent setup checklist (where to put things)

The agent owns translating this doc into code. Suggested:
- `frontend/app/globals.css` — all CSS custom properties for both token sets (light default + `[data-theme="dark"]` or `prefers-color-scheme`). Single source of truth for color/spacing/radius.
- `frontend/tailwind.config.ts` — map the same tokens to Tailwind theme keys (colors, fontFamily, borderRadius, spacing) so utilities stay on-brand.
- `frontend/app/fonts.ts` — `next/font/local` for Magnetar (`--font-display`), `next/font/google` for Barlow (`--font-body`) and JetBrains Mono (`--font-mono`).
- `frontend/public/fonts/magnetar/` — self-hosted Magnetar files (human provides).
- `frontend/public/brand/` — the wing-motif SVG asset(s), logo files, hero photo + helmet PNGs (human provides hero assets).
- A small set of primitive components (Button, Card, Badge, Input) built on tokens first, before any page — so everything downstream is on-brand by default.

Before building hero (§7) or anything needing photos/fonts, the agent should confirm the human has provided the required assets, and ask for them if missing rather than improvising.
