"use client";

// "Who Drinks Mate?" (design: Matessa Landing v4 §8) — faithful 3D dome gallery.
// 35 columns × 5 rows of grayscale tiles on the inside of a rotating sphere
// (CSS perspective + preserve-3d). Pointer-drag spins it with inertia; tapping a
// tile opens a full-image overlay. Client-only mount (no SSR markup mismatch).

import { useEffect, useRef, useState } from "react";
import Image from "next/image";

const IMAGES = [
  "/brand/gal-messi2.webp",
  "/brand/gal-yani.webp",
  "/brand/gal-43.webp",
  "/brand/gal-inch.webp",
  "/brand/gal-suarez.webp",
  "/brand/gal-nymar.webp",
  "/brand/gal-maradona.webp",
  "/brand/gal-review2.webp",
  "/brand/gal-chivara.webp",
  "/brand/gal-alex.webp",
  "/brand/gal-review3.webp",
];

const COLS = 35;
const ROWS = 5;
const RADIUS = 900; // px — sphere radius
const TILE_W = 150;
const TILE_H = 100;

export function DomeGallery() {
  const [mounted, setMounted] = useState(false);
  const [zoom, setZoom] = useState<string | null>(null);
  const stage = useRef<HTMLDivElement>(null);
  const rot = useRef({ x: -8, y: 0 });
  const vel = useRef(0);
  const drag = useRef<{ x: number; y: number } | null>(null);
  const raf = useRef(0);

  useEffect(() => setMounted(true), []);

  useEffect(() => {
    if (!mounted) return;
    const el = stage.current;
    if (!el) return;

    const apply = () => {
      el.style.transform = `translateZ(0) rotateX(${rot.current.x}deg) rotateY(${rot.current.y}deg)`;
    };
    apply();

    const loop = () => {
      // inertia decay after release
      if (!drag.current && Math.abs(vel.current) > 0.02) {
        rot.current.y += vel.current;
        vel.current *= 0.94;
        apply();
      }
      raf.current = requestAnimationFrame(loop);
    };
    raf.current = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf.current);
  }, [mounted]);

  const onDown = (e: React.PointerEvent) => {
    drag.current = { x: e.clientX, y: e.clientY };
    vel.current = 0;
    (e.target as Element).setPointerCapture?.(e.pointerId);
  };
  const onMove = (e: React.PointerEvent) => {
    if (!drag.current) return;
    const dx = e.clientX - drag.current.x;
    const dy = e.clientY - drag.current.y;
    drag.current = { x: e.clientX, y: e.clientY };
    rot.current.y += dx * 0.25;
    rot.current.x = Math.max(-28, Math.min(10, rot.current.x - dy * 0.12));
    vel.current = dx * 0.25;
    stage.current!.style.transform = `translateZ(0) rotateX(${rot.current.x}deg) rotateY(${rot.current.y}deg)`;
  };
  const onUp = () => {
    drag.current = null;
  };

  return (
    <section id="gallery" className="relative overflow-hidden py-16 nav:py-24">
      {/* grayscale backdrop */}
      <div
        aria-hidden="true"
        className="absolute inset-0 opacity-20 grayscale"
        style={{ backgroundImage: "url('/brand/bgdemo.webp')", backgroundSize: "cover", backgroundPosition: "center" }}
      />
      <div className="absolute inset-0 bg-white/70" aria-hidden="true" />

      <div className="relative">
        <h2 className="mb-3 text-center font-heading text-3xl font-bold text-darkgreen md:text-4xl">
          Who Drinks Mate?
        </h2>
        <p className="mb-8 text-center font-body text-xs font-bold uppercase tracking-[0.24em] text-gray-500">
          Drag to Explore · Tap a Photo
        </p>

        {mounted && (
          <div
            className="mx-auto h-[420px] max-w-[1100px] cursor-grab touch-pan-y select-none active:cursor-grabbing nav:h-[520px]"
            style={{ perspective: 1200 }}
            onPointerDown={onDown}
            onPointerMove={onMove}
            onPointerUp={onUp}
            onPointerLeave={onUp}
            role="group"
            aria-label="Draggable photo dome of mate drinkers"
          >
            <div
              ref={stage}
              className="relative left-1/2 top-1/2 h-0 w-0"
              style={{ transformStyle: "preserve-3d" }}
            >
              {Array.from({ length: COLS }).map((_, c) =>
                Array.from({ length: ROWS }).map((__, r) => {
                  const src = IMAGES[(c * ROWS + r) % IMAGES.length];
                  const yaw = (360 / COLS) * c;
                  const pitch = (r - (ROWS - 1) / 2) * 13;
                  return (
                    <button
                      key={`${c}-${r}`}
                      type="button"
                      tabIndex={-1}
                      aria-label="View photo"
                      onClick={() => {
                        if (Math.abs(vel.current) < 1) setZoom(src);
                      }}
                      className="absolute overflow-hidden rounded-lg border border-white/40"
                      style={{
                        width: TILE_W,
                        height: TILE_H,
                        transform: `rotateY(${yaw}deg) rotateX(${pitch}deg) translateZ(${-RADIUS}px)`,
                        backfaceVisibility: "hidden",
                      }}
                    >
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={src}
                        alt=""
                        loading="lazy"
                        draggable={false}
                        className="h-full w-full object-cover grayscale transition-all duration-300 hover:grayscale-0"
                      />
                    </button>
                  );
                }),
              )}
            </div>
          </div>
        )}
      </div>

      {/* zoom overlay */}
      {zoom && (
        <div
          className="fixed inset-0 z-[100] grid place-items-center p-6"
          style={{ background: "rgba(15,20,10,0.55)", backdropFilter: "blur(4px)" }}
          onClick={() => setZoom(null)}
          role="dialog"
          aria-modal="true"
          aria-label="Photo"
        >
          <Image
            src={zoom}
            alt="Mate drinker"
            width={720}
            height={720}
            className="max-h-[80svh] w-auto rounded-2xl shadow-2xl"
          />
          <button
            type="button"
            className="absolute right-5 top-5 grid h-11 w-11 place-items-center rounded-full bg-white/90 font-bold text-darkgreen"
            aria-label="Close photo"
            onClick={() => setZoom(null)}
          >
            ✕
          </button>
        </div>
      )}
    </section>
  );
}
