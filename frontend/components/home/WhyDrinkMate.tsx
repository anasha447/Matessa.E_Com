"use client";

// "Why Drink Mate?" — faithful port of Matessa Landing v4 §Why Drink Mate.
// Tall wrapper (100 + 6×55 svh) with a sticky full-viewport inner. Scroll progress
// paints benefit i: titles reveal progressively (i ≤ idx shown; the active one is
// green and nudged right), panels cross-fade (passed panels exit upward). Icon sits
// on the RIGHT of the copy in a dark-green circle; stat line has a dash prefix.
// Reduced motion → static stacked list.

import { useEffect, useRef, useState, type ReactNode } from "react";

const PER_SVH = 55;

interface Benefit {
  title: string;
  copy: string;
  stat: string;
  icon: ReactNode;
}

const S = { width: 30, height: 30, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 2, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };

const BENEFITS: Benefit[] = [
  {
    title: "Natural Energy",
    copy: "A balanced source of natural caffeine that raises alertness and physical endurance — smooth, steady energy with no spike and no crash.",
    stat: "≈85 mg natural caffeine · steady for hours",
    icon: (
      <svg {...S}>
        <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
      </svg>
    ),
  },
  {
    title: "Boosts Heart Health",
    copy: "Rich in polyphenols and saponins that may improve cholesterol levels and help protect against heart disease with regular drinking.",
    stat: "Supports healthy cholesterol levels",
    icon: (
      <svg {...S}>
        <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
      </svg>
    ),
  },
  {
    title: "Enhances Focus",
    copy: "Caffeine works alongside theobromine for calm, clear-headed concentration — sharp focus without the jitters of coffee.",
    stat: "Caffeine + theobromine, in balance",
    icon: (
      <svg {...S}>
        <circle cx="12" cy="12" r="3" />
        <path d="M12 2v4M12 18v4M2 12h4M18 12h4" />
      </svg>
    ),
  },
  {
    title: "Rich in Antioxidants",
    copy: "Packed with antioxidants, vitamins and minerals that defend your cells and keep your body healthy and strong.",
    stat: "More antioxidants than green tea",
    icon: (
      <svg {...S}>
        <path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z" />
      </svg>
    ),
  },
  {
    title: "Supports Hydration",
    copy: "Sipped slowly, leaf after leaf, mate keeps you refreshed and counts toward your daily fluid intake alongside its nutrients.",
    stat: "Counts toward daily hydration",
    icon: (
      <svg {...S}>
        <path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z" />
      </svg>
    ),
  },
  {
    title: "Aids Digestion",
    copy: "Traditionally shared after meals across South America, mate gently stimulates digestion and helps keep your metabolism in balance.",
    stat: "A post-meal ritual for centuries",
    icon: (
      <svg {...S}>
        <path d="m16 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z" />
        <path d="m2 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z" />
        <path d="M7 21h10M12 3v18" />
      </svg>
    ),
  },
];

export function WhyDrinkMate() {
  const wrap = useRef<HTMLElement>(null);
  const [active, setActive] = useState(0);
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setReduced(true);
      return;
    }
    let ticking = false;
    const update = () => {
      ticking = false;
      const el = wrap.current;
      if (!el) return;
      const r = el.getBoundingClientRect();
      const total = r.height - window.innerHeight;
      const p = total > 0 ? Math.min(1, Math.max(0, -r.top / total)) : 1;
      setActive(Math.min(BENEFITS.length - 1, Math.max(0, Math.floor(p * BENEFITS.length))));
    };
    const onScroll = () => {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(update);
      }
    };
    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
    };
  }, []);

  const titleStyle = (i: number): React.CSSProperties => {
    const shown = reduced || i <= active;
    return {
      margin: 0,
      display: "flex",
      alignItems: "center",
      gap: 12,
      fontWeight: 700,
      fontSize: "clamp(21px, 2.4vw, 33px)",
      lineHeight: 1.2,
      color: i === active || reduced ? "#3E5F2D" : "rgba(47,59,40,0.28)",
      opacity: shown ? 1 : 0,
      transform: !shown ? "translateY(26px)" : i === active && !reduced ? "translateX(10px)" : "none",
      transition: "color 0.4s ease, opacity 0.6s ease, transform 0.7s cubic-bezier(0.22, 0.68, 0.2, 1)",
    };
  };

  const panelStyle = (i: number): React.CSSProperties => {
    const on = i === active;
    return {
      position: "absolute",
      inset: 0,
      display: "flex",
      flexDirection: "column",
      justifyContent: "center",
      gap: "clamp(14px, 2.4vh, 22px)",
      opacity: on ? 1 : 0,
      transform: on ? "none" : i < active ? "translateY(-26px)" : "translateY(26px)",
      pointerEvents: on ? "auto" : "none",
      transition: "opacity 0.45s ease, transform 0.65s cubic-bezier(0.22, 0.68, 0.2, 1)",
    };
  };

  if (reduced) {
    return (
      <section id="benefits" style={{ background: "#F9F7F3", padding: "clamp(48px, 7vw, 90px) clamp(20px, 4vw, 32px)" }}>
        <div style={{ maxWidth: 1120, margin: "0 auto", display: "flex", flexDirection: "column", gap: 32 }}>
          <h2 className="font-heading" style={{ margin: 0, fontWeight: 700, fontSize: "clamp(30px, 3.4vw, 40px)", color: "#2F3B28", textAlign: "center" }}>
            Why Drink Mate?
          </h2>
          {BENEFITS.map((b) => (
            <div key={b.title} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <h3 className="font-heading" style={titleStyle(-1)}>{b.title}</h3>
              <Panel benefit={b} />
            </div>
          ))}
        </div>
      </section>
    );
  }

  return (
    <section id="benefits" ref={wrap} style={{ position: "relative", background: "#F9F7F3", height: `${100 + BENEFITS.length * PER_SVH}svh` }}>
      <div
        style={{
          position: "sticky",
          top: 0,
          minHeight: "100svh",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          gap: "clamp(26px, 4.5vh, 54px)",
          maxWidth: 1120,
          margin: "0 auto",
          padding: "clamp(20px, 3vh, 48px) clamp(20px, 4vw, 32px)",
          overflow: "hidden",
        }}
      >
        <h2 className="font-heading" style={{ margin: 0, fontWeight: 700, fontSize: "clamp(30px, 3.4vw, 40px)", color: "#2F3B28", textAlign: "center" }}>
          Why Drink Mate?
        </h2>
        <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "clamp(26px, 5vw, 80px)" }}>
          {/* Titles */}
          <div style={{ flex: "1 1 340px", minWidth: 270, display: "flex", flexDirection: "column", gap: "clamp(10px, 2vh, 22px)" }}>
            {BENEFITS.map((b, i) => (
              <h3 key={b.title} className="font-heading" style={titleStyle(i)}>
                <span>{b.title}</span>
              </h3>
            ))}
          </div>
          {/* Panels */}
          <div style={{ flex: "1 1 380px", minWidth: 270, position: "relative", minHeight: "clamp(250px, 38vh, 340px)" }}>
            {BENEFITS.map((b, i) => (
              <div key={b.title} style={panelStyle(i)}>
                <Panel benefit={b} />
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function Panel({ benefit }: { benefit: Benefit }) {
  return (
    <>
      <div style={{ display: "flex", flexDirection: "row-reverse", alignItems: "center", justifyContent: "space-between", gap: "clamp(16px, 2.4vw, 26px)" }}>
        <div
          style={{
            flex: "none",
            width: "clamp(56px, 6vw, 72px)",
            height: "clamp(56px, 6vw, 72px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            borderRadius: "50%",
            background: "#2F3B28",
            color: "#EADBA2",
            boxShadow: "0 12px 26px rgba(47,59,40,0.22)",
          }}
        >
          {benefit.icon}
        </div>
        <p style={{ margin: 0, maxWidth: "46ch", fontSize: "clamp(15px, 1.4vw, 18px)", fontWeight: 500, lineHeight: 1.7, color: "#4A5545" }}>
          {benefit.copy}
        </p>
      </div>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 10,
          fontSize: 12.5,
          fontWeight: 700,
          letterSpacing: "0.14em",
          textTransform: "uppercase",
          color: "#3E5F2D",
        }}
      >
        <span style={{ width: 26, height: 2, borderRadius: 2, background: "currentColor", flex: "none" }} />
        <span>{benefit.stat}</span>
      </div>
    </>
  );
}
