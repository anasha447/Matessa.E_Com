"use client";

// "Covering All Aspects of Health" (design: Matessa Landing v4 §4).
// Four orange focus words cycle every 1.5s; a corner-bracket reticle glides to the
// active word (measured live); inactive words blur + dim. CTAs: Try Now → /shop,
// Don't know Mate?! → /what-is-mate.

import { useEffect, useRef, useState } from "react";
import { PillLink } from "@/components/ui/brand";
import { Reveal } from "@/components/Reveal";

const WORDS = ["Energy", "Focus", "Gut Health", "Immunity"];

export function HealthFocus() {
  const [focusI, setFocusI] = useState(0);
  const [box, setBox] = useState<{ x: number; y: number; w: number; h: number } | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const wordRefs = useRef<(HTMLSpanElement | null)[]>([]);

  useEffect(() => {
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const id = reduced ? undefined : setInterval(() => setFocusI((i) => (i + 1) % WORDS.length), 1500);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    const measure = () => {
      const el = wordRefs.current[focusI];
      const host = containerRef.current;
      if (!el || !host) return;
      const a = el.getBoundingClientRect();
      const b = host.getBoundingClientRect();
      setBox({ x: a.left - b.left - 10, y: a.top - b.top - 8, w: a.width + 20, h: a.height + 16 });
    };
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [focusI]);

  return (
    <section id="health" className="relative overflow-hidden py-20 nav:py-28">
      {/* radial glow */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-1/2 top-1/2 h-[560px] w-[860px] -translate-x-1/2 -translate-y-1/2 rounded-full opacity-70"
        style={{ background: "radial-gradient(ellipse, #FDE4D8 0%, transparent 65%)" }}
      />
      <div className="relative mx-auto max-w-3xl px-6 text-center">
        {/* Focus word reticle */}
        <div ref={containerRef} className="relative mx-auto mb-8 flex flex-wrap items-center justify-center gap-x-8 gap-y-3">
          {WORDS.map((w, i) => (
            <span
              key={w}
              ref={(el) => {
                wordRefs.current[i] = el;
              }}
              className="font-body text-sm font-bold uppercase tracking-[0.28em] text-orange transition-all duration-500"
              style={{
                filter: i === focusI ? "blur(0px)" : "blur(4px)",
                opacity: i === focusI ? 1 : 0.4,
                transform: i === focusI ? "scale(1.05)" : "scale(1)",
              }}
            >
              {w}
            </span>
          ))}
          {/* corner brackets */}
          {box && (
            <div
              aria-hidden="true"
              className="pointer-events-none absolute transition-all duration-500 ease-out"
              style={{ transform: `translate(${box.x}px, ${box.y}px)`, width: box.w, height: box.h, left: 0, top: 0, opacity: 0.9 }}
            >
              {(["top-0 left-0 border-t-2 border-l-2", "top-0 right-0 border-t-2 border-r-2", "bottom-0 left-0 border-b-2 border-l-2", "bottom-0 right-0 border-b-2 border-r-2"] as const).map(
                (pos) => (
                  <span key={pos} className={`absolute h-2.5 w-2.5 border-orange ${pos}`} />
                ),
              )}
            </div>
          )}
        </div>

        <Reveal>
          <h2 className="font-heading font-bold text-3xl text-darkgreen md:text-4xl text-balance">
            Covering All Aspects <em className="italic text-orange">of Health</em>
          </h2>
        </Reveal>
        <Reveal delay={110}>
          <p className="mx-auto mt-5 max-w-xl text-gray-600">
            Experience <strong className="text-darkgreen">balanced energy &amp; calm clarity</strong> — a clean, natural
            lift without the jitters,{" "}
            <mark className="bg-yellow/60 px-1 text-darkgreen">elevating your well-being</mark> every day.
          </p>
        </Reveal>
        <Reveal delay={220}>
          <div className="mt-9 flex flex-wrap items-center justify-center gap-4">
            <PillLink href="/shop" variant="orange">
              Try Now
            </PillLink>
            <PillLink href="/what-is-mate" variant="outline">
              Don&apos;t know Mate?!
            </PillLink>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
