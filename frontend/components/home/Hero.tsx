/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useRef } from "react";
import { heroImages } from "@/lib/stubs/home";

interface HeroProps {
  /** wide/desktop variant: full-bleed layout + a "hover to reveal" hint badge. */
  wide?: boolean;
  faceSrc?: string;
  helmetSrc?: string;
}

/** Pointer-reveal hero: a soft circle ("scoop") trails the pointer and reveals the
 *  bare-faced rider through the helmet. The face overlay + mask live in CSS
 *  (.rhero-scoop); here we just ease --scoop-x/--scoop-y toward the pointer each
 *  frame and toggle .is-scooping. Disabled under prefers-reduced-motion. */
export function Hero({
  wide = false,
  faceSrc = heroImages.face,
  helmetSrc = heroImages.helmet,
}: HeroProps = {}) {
  const stageRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    let targetX = 50;
    let targetY = 42;
    let curX = 50;
    let curY = 42;
    let raf = 0;
    let active = false;

    const apply = () => {
      stage.style.setProperty("--scoop-x", `${curX}%`);
      stage.style.setProperty("--scoop-y", `${curY}%`);
    };
    const loop = () => {
      curX += (targetX - curX) * 0.18;
      curY += (targetY - curY) * 0.18;
      apply();
      raf = requestAnimationFrame(loop);
    };
    const track = (e: PointerEvent) => {
      const r = stage.getBoundingClientRect();
      targetX = ((e.clientX - r.left) / r.width) * 100;
      targetY = ((e.clientY - r.top) / r.height) * 100;
    };
    const onEnter = (e: PointerEvent) => {
      active = true;
      stage.classList.add("is-scooping");
      track(e);
      curX = targetX;
      curY = targetY;
      apply();
      if (!raf) raf = requestAnimationFrame(loop);
    };
    const onMove = (e: PointerEvent) => {
      if (active) track(e);
    };
    const onLeave = () => {
      active = false;
      stage.classList.remove("is-scooping");
      if (raf) {
        cancelAnimationFrame(raf);
        raf = 0;
      }
    };

    stage.addEventListener("pointerenter", onEnter);
    stage.addEventListener("pointermove", onMove);
    stage.addEventListener("pointerleave", onLeave);
    return () => {
      stage.removeEventListener("pointerenter", onEnter);
      stage.removeEventListener("pointermove", onMove);
      stage.removeEventListener("pointerleave", onLeave);
      if (raf) cancelAnimationFrame(raf);
    };
  }, []);

  return (
    <section
      className={`rhero${wide ? " is-wide" : ""}`}
      aria-label="Adventure Dose — full-face helmet"
    >
      <div
        className="rhero-stage"
        ref={stageRef}
        tabIndex={0}
        role="img"
        aria-label="Adventure Dose full-face helmet. Hover or drag across it to reveal the rider underneath."
      >
        <img
          className="rhero-layer rhero-face"
          src={faceSrc}
          alt="Rider on an open road at dusk"
          draggable={false}
        />
        <img
          className="rhero-layer rhero-helmet"
          src={helmetSrc}
          alt="Rider wearing the full-face helmet"
          draggable={false}
        />
        <div className="rhero-scoop" aria-hidden="true" />
        <div className="rhero-scrim" aria-hidden="true" />
        {wide && (
          <div className="dk-herohint">
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="m13 2-3 7h5l-4 13 2-9H8z" />
            </svg>
            Hover to reveal the rider
          </div>
        )}
      </div>
    </section>
  );
}
