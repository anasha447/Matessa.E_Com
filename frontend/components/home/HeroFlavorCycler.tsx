"use client";

// Landing hero (design: Matessa Landing v4 §Hero). Three flavors cross-fade on a
// color-field circle: pouches rotate in/out, ingredient art rises in, the H1's
// accent word tints per flavor. Auto-advances every 4s (+1.4s grace on load),
// pauses on hover/touch/hidden tab; horizontal swipe switches on touch.

import { useEffect, useRef, useState } from "react";
import Image from "next/image";

interface Flavor {
  name: string;
  deepText: string;
  circle: string;
  pouch: string;
  /** [behind, front] floating ingredient art */
  ingredients: [string, string];
}

const FLAVORS: Flavor[] = [
  {
    name: "Masala Mate",
    deepText: "#C2500F",
    circle: "radial-gradient(circle at 32% 26%, #F8DCC2 0%, #F2BF92 100%)",
    pouch: "/brand/pouch-masala.webp",
    ingredients: ["/brand/ing-cinnamon-anise.webp", "/brand/ing-cardamom.png"],
  },
  {
    name: "Lemon & Ginger Mate",
    deepText: "#A58114",
    circle: "radial-gradient(circle at 32% 26%, #FAEBBB 0%, #F4D87D 100%)",
    pouch: "/brand/pouch-lemon-ginger.webp",
    ingredients: ["/brand/ing-ginger1.png", "/brand/ing-lemon1.png"],
  },
  {
    name: "Raw Mate",
    deepText: "#8F7A33",
    circle: "radial-gradient(circle at 32% 26%, #F1E9D0 0%, #E4D5A8 100%)",
    pouch: "/brand/pouch-raw.webp",
    ingredients: ["/brand/ing-mate-leaf1.webp", "/brand/ing-mate-leaf2.webp"],
  },
];

const INTERVAL_S = 4;

export function HeroFlavorCycler() {
  const [active, setActive] = useState(0);
  const [prev, setPrev] = useState(-1);
  const [entered, setEntered] = useState(false);
  const hover = useRef(false);
  const touch = useRef<{ x: number; y: number } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setEntered(true), 60);
    return () => clearTimeout(t);
  }, []);

  // Autoplay: re-armed after every change; skips while hovered or tab hidden.
  useEffect(() => {
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced) return;
    const arm = (first: boolean) => {
      timer.current = setTimeout(() => {
        if (!document.hidden && !hover.current) {
          setActive((a) => {
            setPrev(a);
            return (a + 1) % FLAVORS.length;
          });
        }
        arm(false);
      }, (first ? INTERVAL_S + 1.4 : INTERVAL_S) * 1000);
    };
    arm(true);
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  const go = (i: number) =>
    setActive((a) => {
      if (i === a) return a;
      setPrev(a);
      return i;
    });

  const pouchStyle = (i: number): React.CSSProperties => {
    if (i === active) {
      return {
        transform: entered ? "rotate(0deg)" : "rotate(-58deg)",
        opacity: entered ? 1 : 0,
        transition: "transform 1.25s cubic-bezier(0.22, 0.68, 0.14, 1), opacity 0.5s ease 0.12s",
        zIndex: 3,
      };
    }
    if (i === prev) {
      return {
        transform: "rotate(58deg)",
        opacity: 0,
        transition: "transform 1.15s cubic-bezier(0.5, 0.04, 0.62, 1), opacity 0.55s ease 0.32s",
        zIndex: 2,
      };
    }
    return { transform: "rotate(-58deg)", opacity: 0, transition: "none", zIndex: 1 };
  };

  const ingStyle = (flavor: number, delay: number): React.CSSProperties => {
    const on = active === flavor && entered;
    return {
      opacity: on ? 1 : 0,
      transform: on ? "translateY(0) scale(1)" : "translateY(46px) scale(0.72)",
      transition: on
        ? `opacity 0.7s ease ${delay}s, transform 1.05s cubic-bezier(0.22, 0.68, 0.2, 1) ${delay}s`
        : "opacity 0.4s ease, transform 0.5s ease",
    };
  };

  return (
    <section
      id="top"
      aria-label="Hero"
      className="relative overflow-hidden pt-20 nav:pt-28"
      style={{ minHeight: "max(560px, 88svh)" }}
      onMouseEnter={() => (hover.current = true)}
      onMouseLeave={() => (hover.current = false)}
      onTouchStart={(e) => {
        hover.current = true;
        touch.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
      }}
      onTouchEnd={(e) => {
        hover.current = false;
        if (!touch.current) return;
        const dx = e.changedTouches[0].clientX - touch.current.x;
        const dy = e.changedTouches[0].clientY - touch.current.y;
        touch.current = null;
        if (Math.abs(dx) > 42 && Math.abs(dx) > Math.abs(dy) * 1.4) {
          go(dx < 0 ? (active + 1) % FLAVORS.length : (active + 2) % FLAVORS.length);
        }
      }}
    >
      <div className="mx-auto flex max-w-[1360px] flex-col items-center px-6 nav:flex-row nav:items-center">
        {/* Headline */}
        <div className="relative z-10 pt-6 text-center nav:w-[46%] nav:pt-0 nav:text-left">
          <h1 className="font-heading font-bold text-darkgreen [font-size:clamp(42px,5.2vw,74px)] leading-[1.06] tracking-[-0.01em] text-balance">
            The Ritual of{" "}
            <em
              className="italic"
              style={{ color: FLAVORS[active].deepText, transition: "color 0.9s ease" }}
            >
              Natural Energy
            </em>
          </h1>
          {/* Flavor dots (mobile affordance for the swipe) */}
          <div className="mt-8 flex items-center justify-center gap-2 nav:justify-start" role="tablist" aria-label="Flavors">
            {FLAVORS.map((f, i) => (
              <button
                key={f.name}
                role="tab"
                aria-selected={i === active}
                aria-label={f.name}
                onClick={() => go(i)}
                className="h-2.5 rounded-full transition-all duration-300"
                style={{
                  width: i === active ? 26 : 10,
                  background: i === active ? "#2F3B28" : "rgba(47,59,40,0.3)",
                }}
              />
            ))}
          </div>
        </div>

        {/* Pouch stage */}
        <div className="relative w-full nav:w-[54%]" style={{ minHeight: "clamp(430px, 52vw, 640px)" }}>
          {/* Color-field circle */}
          <div
            aria-hidden="true"
            className="absolute left-1/2 top-1/2 aspect-square -translate-x-1/2 -translate-y-1/2 rounded-full"
            style={{
              width: "clamp(420px, 60vw, 760px)",
              background: FLAVORS[active].circle,
              transition: "background 0.9s ease",
            }}
          />
          {/* Ground shadow */}
          <div
            aria-hidden="true"
            className="absolute left-1/2 top-[74%] h-10 w-[52%] -translate-x-1/2 rounded-[50%]"
            style={{ background: "radial-gradient(ellipse, rgba(20,25,15,0.28) 0%, transparent 70%)" }}
          />

          {/* Floating ingredients — behind */}
          {FLAVORS.map((f, i) => (
            <div
              key={`b-${f.name}`}
              aria-hidden="true"
              className="mtsa-float absolute left-[12%] top-[16%] w-[26%]"
              style={{ ...ingStyle(i, 0.35), animationDuration: "6.2s" }}
            >
              <Image src={f.ingredients[0]} alt="" width={220} height={220} className="h-auto w-full" />
            </div>
          ))}

          {/* Pouches */}
          {FLAVORS.map((f, i) => (
            <div
              key={f.pouch}
              className="absolute left-1/2 top-1/2 w-[56%] -translate-x-1/2 -translate-y-1/2 origin-bottom"
              style={pouchStyle(i)}
            >
              <Image
                src={f.pouch}
                alt={`${f.name} pouch`}
                width={480}
                height={620}
                priority={i === 0}
                className="h-auto w-full drop-shadow-[0_28px_38px_rgba(20,25,15,0.28)]"
              />
            </div>
          ))}

          {/* Floating ingredients — front */}
          {FLAVORS.map((f, i) => (
            <div
              key={`f-${f.name}`}
              aria-hidden="true"
              className="mtsa-float absolute right-[10%] top-[58%] z-[4] w-[22%]"
              style={{ ...ingStyle(i, 0.55), animationDuration: "5.4s" }}
            >
              <Image src={f.ingredients[1]} alt="" width={200} height={200} className="h-auto w-full" />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
