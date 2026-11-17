"use client";

// Landing hero. Layout follows design-source/project/Matessa Landing v4.dc.html §Hero (H1
// left, pouch on a color-field circle right); the pouch stage itself now diverges from it:
//
//  - First paint / fallback: a two-sided CSS pouch built from the flat pack art. Tap to flip
//    it and read the back; the flavor's ingredient cut-outs orbit it in CSS on the same
//    loop as the 3D rings, so the hand-over to WebGL is seamless.
//  - Capable devices: a real 3D pouch (hero/PouchScene) fades in over it once its textures
//    load — drag to turn, tap to flip, ingredients orbit it. Skipped under reduced motion,
//    data-saver, low-power devices or when WebGL is missing.
//  - Both modes share the blend tabs and the ingredient chips below the stage; a chip (or a
//    floating cut-out in 3D) highlights that ingredient and shows its taste note.
//  - Phone vs desktop layout is pure CSS (.mtsa-hero* in app/matessa.css), so the server
//    already renders the right one - no desktop frame before the phone layout.
//
// See frontend/animation.hero.md for the full plan and DESIGN-SYSTEM.md §10 for the rules.

import dynamic from "next/dynamic";
import { Component, useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { HERO_FLAVORS, HERO_SPRITES, PACK_ASPECT } from "./hero/flavors";

const PouchScene = dynamic(() => import("./hero/PouchScene"), { ssr: false });

const AUTOPLAY_MS = 7000;
/** After the visitor touches the pouch, leave them alone for this long. */
const QUIET_MS = 10000;
const INK = "#2F3B28";
const CREAM = "#FDF5E6";

/** One lap of the CSS orbit; matches the 3D rings (0.3 rad/s). */
const ORBIT_S = 21;
const RING_TOP = ["26%", "70%"];

/** Where a CSS cut-out sits on its ring, and its static spot when motion is off. */
function orbitSlot(k: number, count: number) {
  const ring = k % 2;
  const inRing = Math.floor(k / 2);
  const ringCount = Math.ceil((count - ring) / 2);
  const f = (inRing / Math.max(1, ringCount) + ring * 0.17) % 1; // fraction of a lap
  const depth = Math.sin(f * Math.PI * 2);
  return {
    top: RING_TOP[ring],
    size: ring === 0 ? 1 : 0.82,
    rotate: ((k * 37) % 30) - 15,
    delayX: -f * ORBIT_S,
    delayDepth: -f * ORBIT_S - ORBIT_S / 4,
    cx: -Math.cos(f * Math.PI * 2),
    scale: 0.74 + 0.32 * ((depth + 1) / 2),
    front: depth > 0,
  };
}

/** Can this device comfortably run the WebGL scene? */
function canRun3D() {
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return false;
  const nav = navigator as Navigator & { deviceMemory?: number; connection?: { saveData?: boolean } };
  if (nav.connection?.saveData) return false;
  if ((nav.hardwareConcurrency ?? 4) < 4) return false;
  if ((nav.deviceMemory ?? 4) < 3) return false;
  try {
    return Boolean(document.createElement("canvas").getContext("webgl2"));
  } catch {
    return false;
  }
}

class SceneBoundary extends Component<{ onError: () => void; children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  componentDidCatch() {
    this.props.onError();
  }
  render() {
    return this.state.failed ? null : this.props.children;
  }
}

export function HeroFlavorCycler() {
  const [active, setActive] = useState(0);
  const [entered, setEntered] = useState(false);
  const [mode, setMode] = useState<"flat" | "3d">("flat");
  const [sceneReady, setSceneReady] = useState(false);
  const [flatGone, setFlatGone] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [flipped, setFlipped] = useState(false);
  const [wantBack, setWantBack] = useState(false);
  const [turns, setTurns] = useState(0);

  const hover = useRef(false);
  const busy = useRef(false);
  const lastTouch = useRef(0);
  const reduced = useRef(false);

  const flavor = HERO_FLAVORS[active];
  const show3D = mode === "3d" && sceneReady;

  useEffect(() => {
    reduced.current = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const raf = requestAnimationFrame(() => {
      setEntered(true);
      if (canRun3D()) setMode("3d");
    });
    return () => cancelAnimationFrame(raf);
  }, []);

  useEffect(() => {
    busy.current = selected !== null || flipped;
  }, [selected, flipped]);

  // Once the 3D pouch has faded in, drop the CSS one so its animations stop running.
  useEffect(() => {
    if (!show3D) return;
    const id = setTimeout(() => setFlatGone(true), 900);
    return () => clearTimeout(id);
  }, [show3D]);

  const go = useCallback((i: number) => {
    setActive((a) => {
      if (i === a) return a;
      setSelected(null);
      setFlipped(false);
      if (!reduced.current) setTurns((t) => t + 1);
      return i;
    });
  }, []);

  // Autoplay — never under reduced motion, and it stays out of the way while the visitor is
  // hovering, reading the back, inspecting an ingredient, or has just turned the pouch.
  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    if (HERO_FLAVORS.length < 2) return;
    const id = setInterval(() => {
      if (document.hidden || hover.current || busy.current) return;
      if (Date.now() - lastTouch.current < QUIET_MS) return;
      setActive((a) => (a + 1) % HERO_FLAVORS.length);
      setTurns((t) => t + 1);
    }, AUTOPLAY_MS);
    return () => clearInterval(id);
  }, []);

  const touched = useCallback(() => {
    lastTouch.current = Date.now();
  }, []);
  const onSceneReady = useCallback(() => setSceneReady(true), []);
  const onSceneError = useCallback(() => {
    setMode("flat");
    setSceneReady(false);
    setFlatGone(false);
  }, []);
  const select = useCallback(
    (name: string | null) => {
      touched();
      setSelected(name);
    },
    [touched],
  );

  const flip = () => {
    touched();
    setWantBack(true);
    setFlipped((f) => !f);
  };

  const selectedIngredient = flavor.ingredients.find((ing) => ing.name === selected);
  const flavorSprites = (i: number) => HERO_SPRITES.filter((s) => s.flavor === i);

  return (
    <section id="top" aria-label="Hero" className="mtsa-hero">
      {/* Desktop: full-bleed color-field circle anchored to the right edge, bleeding
          off-screen so only the left half (reaching centre) shows; the section's
          overflow-x clips the off-screen part. (Mobile circle lives inside the stage.) */}
      <div aria-hidden="true" className="mtsa-hero-circle mtsa-hero-circle--desk" style={{ background: flavor.circle }} />

      <div className="mtsa-hero-inner">
        {/* Left: copy (design shows only the H1) */}
        <div style={{ flex: "1 1 430px", minWidth: 280, position: "relative", zIndex: 5 }}>
          <h1
            className="font-heading"
            style={{
              margin: 0,
              fontWeight: 700,
              fontSize: "clamp(40px, 5.2vw, 74px)",
              lineHeight: 1.06,
              letterSpacing: "-0.01em",
              color: INK,
              textWrap: "balance",
            }}
          >
            The Ritual of{" "}
            <em style={{ fontStyle: "italic", color: flavor.deepText, transition: "color 0.9s ease" }}>
              Natural Energy
            </em>
          </h1>
        </div>

        {/* Right: pouch stage + controls */}
        <div
          className="mtsa-hero-stagecol"
          onMouseEnter={() => (hover.current = true)}
          onMouseLeave={() => (hover.current = false)}
        >
          <div aria-hidden="true" className="mtsa-hero-circle mtsa-hero-circle--mob" style={{ background: flavor.circle }} />

          {/* ── Stage ─────────────────────────────────────────────────────────── */}
          <div className="mtsa-hero-stage">
            <p className="sr-only">Matessa {flavor.name} yerba mate pouch</p>

            {/* ground shadow */}
            <div
              aria-hidden="true"
              style={{
                position: "absolute",
                left: "50%",
                bottom: "7%",
                transform: "translateX(-50%)",
                width: "min(46%, 300px)",
                height: 30,
                background: "radial-gradient(ellipse at center, rgba(24,32,16,0.26), transparent 68%)",
                filter: "blur(7px)",
                opacity: show3D ? 0 : 1,
                transition: "opacity 0.7s ease",
              }}
            />

            {/* CSS pouch + floating cut-outs: first paint, and the fallback when 3D is off */}
            {!flatGone && (
              <div
                aria-hidden={show3D}
                style={{
                  position: "absolute",
                  inset: 0,
                  opacity: show3D ? 0 : 1,
                  pointerEvents: show3D ? "none" : "auto",
                  transition: "opacity 0.7s ease",
                }}
              >
                {HERO_FLAVORS.map((_, fi) => {
                  const sprites = flavorSprites(fi);
                  return sprites.map((s, k) => {
                    const o = orbitSlot(k, sprites.length);
                    const on = fi === active && entered;
                    const lit = selected === s.ingredient;
                    return (
                      // Ring position: z-order flips as it passes behind / in front of the pouch.
                      <div
                        key={`${fi}-${s.src}`}
                        aria-hidden="true"
                        style={{
                          position: "absolute",
                          left: "50%",
                          top: o.top,
                          width: `calc(clamp(58px, 10vw, 128px) * ${o.size})`,
                          pointerEvents: "none",
                          zIndex: o.front ? 3 : 1,
                          animation: `mtsa-orbit-z ${ORBIT_S / 2}s ease-in-out ${o.delayDepth}s infinite alternate`,
                          opacity: on ? (selected && !lit ? 0.4 : 1) : 0,
                          transform: on ? "translate(-50%, -50%)" : "translate(-50%, calc(-50% + 40px)) scale(0.72)",
                          transition: on
                            ? `opacity 0.6s ease ${0.25 + k * 0.06}s, transform 0.9s cubic-bezier(0.22, 0.68, 0.2, 1) ${0.25 + k * 0.06}s`
                            : "opacity 0.35s ease, transform 0.45s ease",
                        }}
                      >
                        <div
                          style={{
                            transform: `translateX(calc(var(--mtsa-orbit-r) * ${o.cx.toFixed(3)}))`,
                            animation: `mtsa-orbit-x ${ORBIT_S / 2}s ease-in-out ${o.delayX}s infinite alternate`,
                          }}
                        >
                          <div
                            style={{
                              transform: `scale(${o.scale.toFixed(3)})`,
                              animation: `mtsa-orbit-depth ${ORBIT_S / 2}s ease-in-out ${o.delayDepth}s infinite alternate`,
                            }}
                          >
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img
                              src={s.src}
                              alt=""
                              draggable={false}
                              loading={fi === 0 ? "eager" : "lazy"}
                              style={{
                                display: "block",
                                width: "100%",
                                transform: `rotate(${o.rotate}deg) scale(${lit ? 1.22 : 1})`,
                                transition: "transform 0.4s ease",
                                filter: "drop-shadow(0 10px 14px rgba(24,32,16,0.18))",
                              }}
                            />
                          </div>
                        </div>
                      </div>
                    );
                  });
                })}

                <div
                  style={{
                    position: "absolute",
                    left: "50%",
                    top: "50%",
                    height: "74%",
                    aspectRatio: String(PACK_ASPECT),
                    transform: `translate(-50%, -50%) translateY(${entered ? 0 : 28}px)`,
                    opacity: entered ? 1 : 0,
                    transition: "transform 0.9s cubic-bezier(0.22, 0.68, 0.14, 1), opacity 0.6s ease",
                    perspective: 1400,
                    zIndex: 2,
                  }}
                >
                  <div style={{ width: "100%", height: "100%", animation: "mtsa-float 6.5s ease-in-out infinite" }}>
                    <button
                      type="button"
                      onClick={flip}
                      onPointerEnter={() => setWantBack(true)}
                      onFocus={() => setWantBack(true)}
                      tabIndex={show3D ? -1 : 0}
                      aria-pressed={flipped}
                      aria-label={`Flip the ${flavor.name} pouch to see the ${flipped ? "front" : "back"}`}
                      className="mtsa-flip"
                      style={{
                        display: "block",
                        position: "relative",
                        width: "100%",
                        height: "100%",
                        padding: 0,
                        border: "none",
                        background: "none",
                        cursor: "pointer",
                        borderRadius: "4.5% / 2.7%",
                        transformStyle: "preserve-3d",
                        transform: `rotateY(${turns * 360 + (flipped ? 180 : 0)}deg)`,
                        transition: "transform 1.1s cubic-bezier(0.3, 0.1, 0.2, 1)",
                      }}
                    >
                      {(["front", "back"] as const).map((face) => (
                        <span
                          key={face}
                          style={{
                            position: "absolute",
                            inset: 0,
                            backfaceVisibility: "hidden",
                            transform: face === "back" ? "rotateY(180deg)" : undefined,
                            filter: "drop-shadow(0 22px 30px rgba(24,32,16,0.22))",
                          }}
                        >
                          {HERO_FLAVORS.map((f, fi) =>
                            face === "back" && !wantBack ? null : (
                              // eslint-disable-next-line @next/next/no-img-element
                              <img
                                key={f.slug}
                                src={f[face]}
                                alt=""
                                draggable={false}
                                fetchPriority={face === "front" && fi === 0 ? "high" : undefined}
                                style={{
                                  position: "absolute",
                                  inset: 0,
                                  width: "100%",
                                  height: "100%",
                                  userSelect: "none",
                                  opacity: fi === active ? 1 : 0,
                                  transition: "opacity 0.25s ease 0.3s",
                                }}
                              />
                            ),
                          )}
                          {/* soft pouch shading: darker seams at the sides, a highlight off-centre */}
                          <span
                            aria-hidden="true"
                            style={{
                              position: "absolute",
                              inset: 0,
                              borderRadius: "4.5% / 2.7%",
                              background:
                                "linear-gradient(90deg, rgba(0,0,0,0.16) 0%, rgba(0,0,0,0) 14%, rgba(255,255,255,0.13) 36%, rgba(255,255,255,0) 58%, rgba(0,0,0,0.14) 100%), linear-gradient(180deg, rgba(0,0,0,0.10) 0%, rgba(0,0,0,0) 5%, rgba(0,0,0,0) 90%, rgba(0,0,0,0.14) 100%)",
                            }}
                          />
                        </span>
                      ))}
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* 3D pouch + orbiting ingredients */}
            {mode === "3d" && (
              <div
                className="mtsa-hero-3d"
                style={{
                  opacity: show3D ? 1 : 0,
                  pointerEvents: show3D ? "auto" : "none",
                }}
              >
                <SceneBoundary onError={onSceneError}>
                  <PouchScene
                    active={active}
                    selected={selected}
                    onSelect={select}
                    onReady={onSceneReady}
                    onInteract={touched}
                  />
                </SceneBoundary>
              </div>
            )}
          </div>

          {/* ── Controls: blend tabs, ingredient chips, taste note ─────────────── */}
          <div style={{ position: "relative", zIndex: 6, display: "grid", gap: 12, justifyItems: "center" }}>
            <div role="tablist" aria-label="Choose a blend" style={{ display: "flex", gap: 8 }}>
              {HERO_FLAVORS.map((f, i) => {
                const on = i === active;
                return (
                  <button
                    key={f.slug}
                    type="button"
                    role="tab"
                    aria-selected={on}
                    onClick={() => {
                      touched();
                      go(i);
                    }}
                    className="font-body"
                    style={{
                      minHeight: 40,
                      padding: "0 18px",
                      borderRadius: 999,
                      border: `1.5px solid ${on ? INK : "rgba(47,59,40,0.35)"}`,
                      background: on ? INK : "rgba(255,255,255,0.35)",
                      color: on ? CREAM : INK,
                      fontSize: 14,
                      fontWeight: 700,
                      letterSpacing: "0.02em",
                      cursor: "pointer",
                      transition: "background 0.3s ease, color 0.3s ease, border-color 0.3s ease",
                    }}
                  >
                    {f.name}
                  </button>
                );
              })}
            </div>

            {/* Phones: one swipeable row. Wider: wrap and centre. */}
            <ul aria-label={`What is in ${flavor.name}`} className="mtsa-hero-chips">
              {flavor.ingredients.map((ing) => {
                const on = ing.name === selected;
                return (
                  <li key={ing.name} style={{ flex: "0 0 auto" }}>
                    <button
                      type="button"
                      aria-pressed={on}
                      onClick={() => select(on ? null : ing.name)}
                      className="font-body"
                      style={{
                        minHeight: 36,
                        padding: "0 14px",
                        borderRadius: 999,
                        border: `1px solid ${on ? flavor.deepText : "rgba(47,59,40,0.22)"}`,
                        background: on ? flavor.deepText : "rgba(255,255,255,0.6)",
                        color: on ? "#FFFFFF" : INK,
                        fontSize: 13,
                        fontWeight: 600,
                        whiteSpace: "nowrap",
                        cursor: "pointer",
                        transition: "background 0.25s ease, color 0.25s ease, border-color 0.25s ease",
                      }}
                    >
                      {ing.name}
                    </button>
                  </li>
                );
              })}
            </ul>

            <p
              aria-live="polite"
              className="font-body mtsa-hero-note"
              style={{
                margin: 0,
                minHeight: "2.9em",
                maxWidth: "36ch",
                textAlign: "center",
                fontSize: 14,
                lineHeight: 1.45,
                color: INK,
              }}
            >
              {selectedIngredient ? (
                <>
                  <strong>{selectedIngredient.name}.</strong> {selectedIngredient.benefit}
                </>
              ) : (
                <span style={{ opacity: 0.72 }}>
                  {show3D
                    ? "Drag the pouch to turn it. Pick an ingredient to see what it brings."
                    : "Tap the pouch to flip it. Pick an ingredient to see what it brings."}
                </span>
              )}
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
