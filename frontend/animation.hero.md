# 3D hero animation — plan and build notes

Status: **built** for Masala and Lemon & Ginger (Turning Pouch + Recipe Orbit, CSS flip fallback). Raw Mate waits on its pack art.

Differences from the plan below, as built:

- Blend switching is by named tabs (Masala / Lemon & Ginger) on every screen size; the old dots and swipe are gone because horizontal drag now turns the pouch.
- Tapping the 3D pouch flips it, same as the fallback.
- Extra cut-outs were taken from `design-source/project/assets` (clove, star anise, ginger slices, second lemon, second cinnamon) and added to `public/brand/` as WebP. Bay leaf, black pepper and nutmeg still have no cut-out, so they appear as chips only.
- The 3D scene also runs on phones that pass the capability check (4+ cores, 3 GB+ memory, WebGL2, no data-saver, no reduced motion); everything else gets the CSS pouch.

## Context

The landing hero (`components/home/HeroFlavorCycler.tsx`) cycles flat pouch photos with a few floating ingredient cut-outs. Front and back print art now exists for Masala and Lemon & Ginger, so the hero can show real 3D pouches with both sides and present each flavor's ingredients in a way people can explore.

## Source material

- Print art (main checkout, `frontend/public/packaging details/`): front + back JPGs for **Lemon & Ginger** and **Masala** (2550x3300, white margins), plus the two small pouch photos.
- Pouch shape: stand-up zip pouch, rounded top corners, tear notches, bulged base.
- Recipes printed on the back:
  - Lemon & Ginger: 95% mate leaves, 4% lemon, 1% ginger.
  - Masala: 95% mate leaves, 2% cinnamon, 1% bay leaf, 0.8% star anise, 0.5% each ginger, cardamom, clove, black pepper, 0.2% nutmeg.
- Ingredient cut-outs already in `public/brand/`: mate leaf (x2), lemon, ginger, cinnamon, cardamom, cinnamon + star anise.

### Gaps

- Raw Mate has no front/back art yet (art is coming).
- No cut-outs for clove, bay leaf, black pepper, nutmeg, or star anise on its own.
- Label art issues to fix before print: Masala recipe sums to 101%; typos "Black Papper", "Verba Mate", "MaTeesa".
- No 3D library installed yet (`three` would be new).

## The five ideas considered

1. **Turning Pouch** — real 3D pouch with front/back art, idle rotation, drag to spin; flavor change spins it 180° and swaps the art.
2. **Burst Open** — zip opens, ingredients fly out and settle into a labelled arc; next flavor pulls them back in.
3. **Scroll Story** — pinned hero; pouch turns front to back on scroll, the pack's line drawings become real ingredient photos, pouch docks into "Our Blends".
4. **Recipe Orbit** — ingredients circle the pouch on tilted rings; hover/tap one to bring it forward with its name and benefit; chips on mobile.
5. **Flip Pouch** — CSS-only two-sided pouch with pointer tilt, sheen and tap-to-flip; no 3D library.

**Chosen: 1 + 4, with 5 as the fallback.**

## Decisions

- **Two 3D pouches now** (Masala, Lemon & Ginger), each with front and back art. The hero is config-driven so Raw Mate is one config entry plus two textures when its art arrives.
- Highlighted ingredient shows **name + one-line benefit**, no percentages.
- Open assumption (not yet confirmed): one pouch is centre stage at a time, the other spins in on flavor change; they are not shown side by side.

## What gets built

### 1. Textures
- Crop each JPG to the art rectangle, export WebP ~1024px wide into `public/brand/pack/`: `masala-front.webp`, `masala-back.webp`, `lemon-front.webp`, `lemon-back.webp`.
- One-off crop with `sharp`; the script is throwaway, not committed.

### 2. Hero data — `components/home/hero/flavors.ts` (new)
- Moves `FLAVORS` out of the component and extends it: `slug`, colors (existing `deepText`, `circle`), `pouch` (2D photo, kept for first paint and fallback), optional `front`/`back` textures (absent = no 3D yet, which is how Raw waits), `ingredients[]` = `{ name, benefit, img?, ring }`.
- All nine Masala and three Lemon ingredients listed by name. Those without a cut-out appear in the chip list only, not in the orbit.
- Benefit lines are sensory/taste notes ("warming", "zesty"), not medical claims.

### 3. 3D scene — `components/home/hero/PouchScene.tsx` (new, client only)
- Adds `three` + `@react-three/fiber` (v9, React 19 compatible). No drei, no environment-map download.
- **Pouch:** built in code — two subdivided planes (front, back) displaced into a stand-up pouch profile (flat seal at top and sides, belly toward the base), rounded top corners via texture alpha. Matte-foil `MeshStandardMaterial`, hemisphere + one directional light, contact shadow as today's CSS shadow.
- **Motion:** slow idle sway; drag (pointer/touch) to rotate freely, release eases back. Flavor change = 180° spin with textures swapped at the edge-on moment; color-circle transition stays as today.
- **Orbit:** ingredient cut-outs as billboards on two or three tilted rings, passing truly behind and in front of the pouch. Hover/tap pauses the orbit, brings that ingredient forward and shows a DOM label (name + benefit).
- Render loop pauses when the hero is off-screen or the tab is hidden; DPR capped at 2.

### 4. Hero shell — `components/home/HeroFlavorCycler.tsx` (edit)
- Keeps the H1, color circle, autoplay / hover-pause / swipe / dots logic, and the 2D pouch image as server-rendered first paint (protects LCP).
- Loads `PouchScene` with `next/dynamic` (`ssr: false`) after mount and cross-fades from the 2D pouch once textures are ready.
- **Ingredient chips:** a row of real buttons under the stage (name; benefit on focus/tap), synced with the orbit highlight. This is the keyboard and screen-reader path and the main mobile UI.
- **Fallback** (no WebGL, `prefers-reduced-motion`, low-power or narrow devices): CSS two-sided pouch using the same textures — pointer tilt, tap/click to flip to the back, existing floating cut-outs. Reduced motion gets no autoplay and no tilt, flip only.

### 5. Docs
- `DESIGN-SYSTEM.md` motion/hero section: record that the hero now diverges from `Matessa Landing v4.dc.html` (3D pouch, orbit, chips, fallback rules).

## Not in scope

- Raw Mate 3D (waits for art), new ingredient cut-outs, any backend/API change. Ingredient copy lives in the hero config as brand copy, same as today's hardcoded pouch art.

## Verification (when built)

- `npm run lint` and `npm run build`; confirm three.js lands in its own lazy chunk, not the landing page's first-load JS.
- Dev server on port 3100: no console errors; 2D pouch paints first then 3D fades in; drag rotates and shows the back art; dots, swipe and autoplay switch flavors with the spin; tapping an ingredient and its chip highlight each other.
- 375px viewport and emulated reduced motion: CSS fallback renders, flips, no autoplay.
