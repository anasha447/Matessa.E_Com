"use client";

// Curved marquee band under the hero (design: Matessa Landing v4 §Marquee).
// Cream Quicksand caps scroll along a curved <textPath> inside a dark-green band
// with a yellow top stroke; driven by rAF (speed ~40px/s). Reduced motion or no-JS
// gets the static band (text parked mid-path).

import { useEffect, useRef } from "react";

const LINE =
  "NATURALLY CAFFEINATED  ·  HAND-BLENDED IN SMALL BATCHES  ·  SINGLE-ORIGIN LEAF  ·  BREWED HOT OR ICED  ·  ";

export function CurvedMarquee() {
  const desktopTp = useRef<SVGTextPathElement>(null);
  const mobileTp = useRef<SVGTextPathElement>(null);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let raf = 0;
    let last = performance.now();
    let offset = 0;
    let paused = false;
    const SPEED = 40; // px/s along the path
    const LOOP = 1600; // one repetition width; text is repeated so the wrap is seamless

    const tick = (now: number) => {
      const dt = (now - last) / 1000;
      last = now;
      if (!paused) {
        offset = (offset + SPEED * dt) % LOOP;
        const val = `${-offset}`;
        desktopTp.current?.setAttribute("startOffset", val);
        mobileTp.current?.setAttribute("startOffset", val);
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);

    const host = document.getElementById("mtsa-marquee");
    const on = () => (paused = true);
    const off = () => (paused = false);
    host?.addEventListener("mouseenter", on);
    host?.addEventListener("mouseleave", off);
    return () => {
      cancelAnimationFrame(raf);
      host?.removeEventListener("mouseenter", on);
      host?.removeEventListener("mouseleave", off);
    };
  }, []);

  const text = LINE.repeat(4);
  const textStyle = {
    fontFamily: "var(--font-body)",
    fontSize: 14,
    fontWeight: 700,
    letterSpacing: "3.4px",
    fill: "#EADBA2",
  } as const;

  return (
    <div id="mtsa-marquee" aria-hidden="true" className="relative z-10 -mt-6 nav:-mt-10">
      {/* Desktop curve */}
      <svg viewBox="0 0 1440 130" className="hidden w-full nav:block" style={{ height: "auto" }}>
        <path d="M0,78 C420,124 980,110 1440,62 L1440,131 L0,131 Z" fill="#F9F7F3" />
        <path d="M0,30 C420,76 980,62 1440,14 L1440,62 C980,110 420,124 0,78 Z" fill="#2F3B28" />
        <path d="M0,30 C420,76 980,62 1440,14" fill="none" stroke="#F3CB57" strokeWidth="2.5" />
        <path id="mtsa-mq-path" d="M0,54 C420,100 980,86 1440,38" fill="none" />
        <text dominantBaseline="middle" style={textStyle}>
          <textPath ref={desktopTp} href="#mtsa-mq-path" startOffset="0">
            {text}
          </textPath>
        </text>
      </svg>
      {/* Mobile curve */}
      <svg viewBox="0 0 720 130" className="w-full nav:hidden" style={{ height: "auto" }}>
        <path d="M0,84 C210,122 490,112 720,70 L720,131 L0,131 Z" fill="#F9F7F3" />
        <path d="M0,32 C210,70 490,60 720,18 L720,70 C490,112 210,122 0,84 Z" fill="#2F3B28" />
        <path d="M0,32 C210,70 490,60 720,18" fill="none" stroke="#F3CB57" strokeWidth="2.5" />
        <path id="mtsa-mq-path-m" d="M0,56 C210,94 490,84 720,42" fill="none" />
        <text dominantBaseline="middle" style={textStyle}>
          <textPath ref={mobileTp} href="#mtsa-mq-path-m" startOffset="0">
            {text}
          </textPath>
        </text>
      </svg>
    </div>
  );
}
