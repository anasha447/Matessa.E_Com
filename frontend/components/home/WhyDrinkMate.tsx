"use client";

// "Why Drink Mate?" (design: Matessa Landing v4 §6) — pinned scroll story.
// The outer section is tall (430svh); the inner viewport is sticky. Scroll progress
// scrubs through 6 benefits: title list on the left (active = green, slides in),
// cross-fading icon panels on the right. Reduced motion → static stacked list.

import { useEffect, useRef, useState } from "react";
import { LeafIcon, HeartPulseIcon, ZapIcon, ShieldIcon, DropletIcon, FlameIcon } from "@/components/ui/icons";

const BENEFITS = [
  {
    title: "Natural Energy",
    copy:
      "Yerba mate delivers a clean, plant-based lift. The caffeine arrives with theobromine and antioxidants, so the energy feels steady — not spiky.",
    stat: "≈85 MG NATURAL CAFFEINE · STEADY FOR HOURS",
    Icon: ZapIcon,
  },
  {
    title: "Boosts Heart Health",
    copy:
      "Mate's polyphenols and saponins are studied for their role in supporting cardiovascular health as part of a balanced routine.",
    stat: "SUPPORTS HEALTHY CHOLESTEROL LEVELS",
    Icon: HeartPulseIcon,
  },
  {
    title: "Enhances Focus",
    copy:
      "Caffeine and theobromine in natural balance sharpen attention without the edge — the reason mate is the long-work-session drink.",
    stat: "CAFFEINE + THEOBROMINE, IN BALANCE",
    Icon: FlameIcon,
  },
  {
    title: "Rich in Antioxidants",
    copy:
      "Cup for cup, yerba mate brings more antioxidant capacity than green tea — chlorogenic acids, polyphenols and more.",
    stat: "MORE ANTIOXIDANTS THAN GREEN TEA",
    Icon: LeafIcon,
  },
  {
    title: "Supports Hydration",
    copy:
      "Brewed light and sipped slowly through the day, mate counts toward your daily fluids — the ritual keeps you drinking.",
    stat: "COUNTS TOWARD DAILY HYDRATION",
    Icon: DropletIcon,
  },
  {
    title: "Aids Digestion",
    copy:
      "In South America, mate after a meal is tradition. Its gentle bitterness has closed lunches for centuries.",
    stat: "A POST-MEAL RITUAL FOR CENTURIES",
    Icon: ShieldIcon,
  },
];

export function WhyDrinkMate() {
  const wrap = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setReduced(true);
      return;
    }
    let raf = 0;
    const onScroll = () => {
      if (raf) return;
      raf = requestAnimationFrame(() => {
        raf = 0;
        const el = wrap.current;
        if (!el) return;
        const rect = el.getBoundingClientRect();
        const total = rect.height - window.innerHeight;
        const progress = Math.min(1, Math.max(0, -rect.top / Math.max(1, total)));
        setActive(Math.min(BENEFITS.length - 1, Math.floor(progress * BENEFITS.length)));
      });
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      cancelAnimationFrame(raf);
    };
  }, []);

  if (reduced) {
    return (
      <section id="benefits" className="py-20">
        <div className="mx-auto max-w-4xl px-6">
          <h2 className="mb-12 text-center font-heading text-3xl font-bold text-darkgreen md:text-4xl">
            Why Drink Mate?
          </h2>
          <div className="grid gap-8 sm:grid-cols-2">
            {BENEFITS.map((b) => (
              <BenefitPanel key={b.title} benefit={b} />
            ))}
          </div>
        </div>
      </section>
    );
  }

  return (
    <section id="benefits" ref={wrap} style={{ height: "430svh" }}>
      <div className="sticky top-0 flex min-h-[100svh] flex-col justify-center overflow-hidden">
        <div className="mx-auto w-full max-w-[1100px] px-6">
          <h2 className="mb-10 text-center font-heading text-3xl font-bold text-darkgreen md:text-4xl nav:mb-16">
            Why Drink Mate?
          </h2>
          <div className="grid items-center gap-10 nav:grid-cols-[1fr_1.1fr] nav:gap-16">
            {/* Titles */}
            <ol className="order-2 space-y-3.5 nav:order-1 nav:space-y-5">
              {BENEFITS.map((b, i) => (
                <li key={b.title}>
                  <span
                    className="block font-heading text-xl font-bold transition-all duration-500 nav:text-[26px]"
                    style={{
                      color: i === active ? "#3E5F2D" : "rgba(47,59,40,0.28)",
                      transform: i === active ? "translateX(10px)" : "translateX(0)",
                    }}
                  >
                    {b.title}
                  </span>
                </li>
              ))}
            </ol>
            {/* Panels */}
            <div className="relative order-1 min-h-[240px] nav:order-2 nav:min-h-[300px]">
              {BENEFITS.map((b, i) => (
                <div
                  key={b.title}
                  className="absolute inset-0 transition-all duration-500"
                  style={{
                    opacity: i === active ? 1 : 0,
                    transform: i === active ? "translateY(0)" : "translateY(14px)",
                    pointerEvents: i === active ? "auto" : "none",
                  }}
                >
                  <BenefitPanel benefit={b} />
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function BenefitPanel({ benefit }: { benefit: (typeof BENEFITS)[number] }) {
  const { Icon } = benefit;
  return (
    <div className="flex flex-row-reverse items-center gap-6 rounded-[22px] bg-white p-6 shadow-lg nav:p-8">
      <span className="grid h-16 w-16 shrink-0 place-items-center rounded-full bg-darkgreen text-craemy">
        <Icon size={28} />
      </span>
      <div>
        <p className="text-gray-600">{benefit.copy}</p>
        <p className="mt-3 font-body text-[11px] font-bold uppercase tracking-[0.18em] text-green">{benefit.stat}</p>
      </div>
    </div>
  );
}
