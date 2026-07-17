"use client";

// "Our Blends" cards (design: Matessa Landing v4 §5) — API-wired version of the
// design's three static cards. Tinted card per blend, leaf-pattern overlay that
// pans and fades in on hover, pouch lifts + straightens, Add-to-Cart slides in.
// Mobile: scroll-snap carousel; the centered card counts as "hovered".

import { useRef, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useCart } from "@/components/cart/CartContext";
import { formatMoney } from "@/lib/utils/money";
import { Reveal } from "@/components/Reveal";

export interface BlendCardData {
  name: string;
  slug: string;
  price: number;
  image: string;
  /** default variant used for quick add-to-cart; null disables the button */
  variantId: number | null;
}

interface Skin {
  bg: string;
  overlay: string;
  label: string;
  labelColor: string;
}

// Per-slug visual config from the design; neutral fallback for future products.
const SKINS: Record<string, Skin> = {
  "masala-mate": { bg: "#F7E3CF", overlay: "#F26323", label: "Cinnamon · Cardamom · Clove", labelColor: "#C2500F" },
  "lemon-ginger-mate": { bg: "#F9EDC6", overlay: "#F3CB57", label: "Lemon Peel · Ginger Root", labelColor: "#A58114" },
  "raw-mate": { bg: "#EFE7CD", overlay: "#EADBA2", label: "Pure Loose Leaf", labelColor: "#8F7A33" },
};
const FALLBACK_SKIN: Skin = { bg: "#EFEAD8", overlay: "#7A9D3E", label: "Small-Batch Blend", labelColor: "#5E7442" };

export function BlendCards({ blends }: { blends: BlendCardData[] }) {
  const [hovered, setHovered] = useState(-1);
  const [added, setAdded] = useState<string | null>(null);
  const { addItem, open } = useCart();
  const scroller = useRef<HTMLDivElement>(null);
  const [centered, setCentered] = useState(0);

  const onScroll = () => {
    const s = scroller.current;
    if (!s) return;
    const c = s.scrollLeft + s.clientWidth / 2;
    let best = 0;
    let bd = Number.POSITIVE_INFINITY;
    Array.from(s.children).forEach((k, i) => {
      const el = k as HTMLElement;
      const d = Math.abs(el.offsetLeft + el.offsetWidth / 2 - c);
      if (d < bd) {
        bd = d;
        best = i;
      }
    });
    setCentered(best);
  };

  const add = async (b: BlendCardData) => {
    if (b.variantId == null) return;
    try {
      await addItem(b.variantId, 1);
      setAdded(b.slug);
      setTimeout(() => setAdded(null), 1600);
      open();
    } catch {
      // cart errors surface in the drawer; keep the card calm
    }
  };

  return (
    <section id="shop" aria-label="Three Blends One Leaf" className="py-16 nav:py-24">
      <div className="mx-auto max-w-[1200px] px-5 nav:px-12">
        <Reveal>
          <h2 className="mb-10 text-center font-heading text-3xl font-bold text-darkgreen md:text-4xl">Our Blends</h2>
        </Reveal>

        <div
          ref={scroller}
          onScroll={onScroll}
          className="flex snap-x snap-mandatory gap-3.5 overflow-x-auto pb-5 [-webkit-overflow-scrolling:touch] [scrollbar-width:none] nav:grid nav:snap-none nav:gap-7 nav:overflow-visible nav:pb-0 nav:[grid-template-columns:repeat(auto-fit,minmax(290px,1fr))]"
        >
          {blends.map((b, i) => {
            const skin = SKINS[b.slug] ?? FALLBACK_SKIN;
            const hot = hovered === i || centered === i;
            return (
              <Reveal key={b.slug} delay={i * 110} className="min-w-0 flex-[0_0_80%] snap-center nav:flex-none">
                <div
                  onMouseEnter={() => setHovered(i)}
                  onMouseLeave={() => setHovered(-1)}
                  className="relative flex h-full flex-col items-start gap-3.5 overflow-hidden rounded-[22px] p-6 nav:p-8"
                  style={{ background: skin.bg }}
                >
                  {/* leaf-pattern pan overlay */}
                  <div
                    aria-hidden="true"
                    className="mtsa-pan pointer-events-none absolute inset-0 transition-opacity duration-700"
                    style={{
                      backgroundImage: "url('/brand/leaf-pattern-light.webp')",
                      backgroundSize: "340px",
                      backgroundBlendMode: "multiply",
                      backgroundColor: skin.overlay,
                      opacity: hot ? 0.85 : 0,
                      mixBlendMode: "multiply",
                    }}
                  />
                  <Link href={`/product/${b.slug}`} className="relative block w-full" aria-label={b.name}>
                    <Image
                      src={b.image}
                      alt={`${b.name} pouch`}
                      width={380}
                      height={460}
                      className="mx-auto h-[240px] w-auto object-contain transition-transform duration-700 nav:h-[280px]"
                      style={{ transform: hot ? "rotate(0deg) translateY(-8px)" : "rotate(-6deg)" }}
                    />
                  </Link>
                  <div
                    className="relative font-body text-[11px] font-bold uppercase tracking-[0.22em]"
                    style={{ color: skin.labelColor }}
                  >
                    {skin.label}
                  </div>
                  <h3 className="relative font-heading text-xl font-bold text-darkgreen">
                    <Link href={`/product/${b.slug}`}>{b.name}</Link>
                  </h3>
                  <div className="relative flex w-full items-center justify-between gap-3">
                    <span className="font-body text-lg font-bold text-green">{formatMoney(b.price)}</span>
                    <button
                      type="button"
                      onClick={() => add(b)}
                      disabled={b.variantId == null}
                      className="rounded-full bg-green px-5 py-2.5 font-body text-sm font-bold text-white transition-all duration-300 hover:bg-orange disabled:opacity-40"
                      style={{
                        transform: hot ? "translateY(0)" : "translateY(14px)",
                        opacity: hot ? 1 : 0,
                      }}
                    >
                      {added === b.slug ? "Added ✓" : "Add to Cart"}
                    </button>
                  </div>
                </div>
              </Reveal>
            );
          })}
        </div>
      </div>
    </section>
  );
}
