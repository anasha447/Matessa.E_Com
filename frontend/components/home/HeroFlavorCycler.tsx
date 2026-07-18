"use client";

// Landing hero — faithful port of design-source/project/Matessa Landing v4.dc.html §Hero.
// The color-field circle is a full-bleed element anchored to the RIGHT viewport edge so
// it reads as a half-circle reaching the screen centre; the section clips horizontal
// overflow so the full circle can never be revealed by scrolling right. Autoplay 4s
// (+1.4s first), pauses on hover/touch/hidden tab; swipe or the dots switch flavors.

import { useEffect, useRef, useState } from "react";

interface Flavor {
  deepText: string;
  circle: string;
  pouch: string;
  pouchAlt: string;
}

const FLAVORS: Flavor[] = [
  {
    deepText: "#C2500F",
    circle: "radial-gradient(circle at 32% 26%, #F8DCC2 0%, #F2BF92 100%)",
    pouch: "/brand/pouch-masala.webp",
    pouchAlt: "Matessa Masala yerba mate pouch",
  },
  {
    deepText: "#A58114",
    circle: "radial-gradient(circle at 32% 26%, #FAEBBB 0%, #F4D87D 100%)",
    pouch: "/brand/pouch-lemon-ginger.webp",
    pouchAlt: "Matessa Lemon and Ginger yerba mate pouch",
  },
  {
    deepText: "#8F7A33",
    circle: "radial-gradient(circle at 32% 26%, #F1E9D0 0%, #E4D5A8 100%)",
    pouch: "/brand/pouch-raw.webp",
    pouchAlt: "Matessa Raw Mate pure loose leaf pouch",
  },
];

// Behind-the-pouch ingredients (one per flavor). Mobile positions pull them to the very
// edge + shrink them so they frame the centred pouch instead of overlapping it.
const ING_BEHIND = [
  { src: "/brand/ing-cinnamon-anise.webp", alt: "Cinnamon sticks and star anise", left: "5%", bottom: "10%", width: "clamp(110px, 12vw, 170px)", rotate: "-8deg", anim: "mtsa-float 6.4s ease-in-out -1.2s infinite" },
  { src: "/brand/ing-ginger1.png", alt: "Ginger root", left: "5%", bottom: "8%", width: "clamp(96px, 11vw, 152px)", rotate: "-6deg", anim: "mtsa-float 7.2s ease-in-out -2.4s infinite" },
  { src: "/brand/ing-mate-leaf1.webp", alt: "Yerba mate leaves", left: "7%", bottom: "6%", width: "clamp(96px, 11vw, 150px)", rotate: "-10deg", anim: "mtsa-float 6.8s ease-in-out -1.6s infinite" },
];

const ING_FRONT = [
  { src: "/brand/ing-cardamom.png", alt: "Cardamom pods", right: "15%", bottom: "14%", width: "clamp(56px, 6.5vw, 90px)", rotate: "20deg", anim: "mtsa-float 5.9s ease-in-out -1.8s infinite" },
  { src: "/brand/ing-lemon1.png", alt: "Lemon", right: "7%", bottom: "14%", width: "clamp(76px, 9vw, 126px)", rotate: "8deg", anim: "mtsa-float 5.6s ease-in-out infinite" },
  { src: "/brand/ing-mate-leaf2.webp", alt: "Yerba mate leaf branch", right: "9%", bottom: "5%", width: "clamp(88px, 10vw, 140px)", rotate: "12deg", anim: "mtsa-float 5.4s ease-in-out infinite" },
];

const INTERVAL_S = 4;

function useIsMobile() {
  const [mobile, setMobile] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia("(max-width: 840px)");
    const on = () => setMobile(mq.matches);
    on();
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, []);
  return mobile;
}

export function HeroFlavorCycler() {
  const [active, setActive] = useState(0);
  const [prev, setPrev] = useState(-1);
  const [entered, setEntered] = useState(false);
  const isMobile = useIsMobile();
  const hover = useRef(false);
  const touch = useRef<{ x: number; y: number } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const raf = requestAnimationFrame(() => setEntered(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const arm = (first: boolean) => {
      timer.current = setTimeout(() => {
        if (!document.hidden && !hover.current) {
          setActive((a) => {
            setPrev(a);
            return (a + 1) % 3;
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
    const base: React.CSSProperties = {
      position: "absolute",
      inset: 0,
      display: "flex",
      alignItems: "flex-end",
      justifyContent: "center",
      transformOrigin: "50% 230%",
      willChange: "transform, opacity",
    };
    if (i === active) {
      return {
        ...base,
        transform: entered ? "rotate(0deg)" : "rotate(-58deg)",
        opacity: entered ? 1 : 0,
        transition: "transform 1.25s cubic-bezier(0.22, 0.68, 0.14, 1), opacity 0.5s ease 0.12s",
        zIndex: 3,
      };
    }
    if (i === prev) {
      return {
        ...base,
        transform: "rotate(58deg)",
        opacity: 0,
        transition: "transform 1.15s cubic-bezier(0.5, 0.04, 0.62, 1), opacity 0.55s ease 0.32s",
        zIndex: 2,
      };
    }
    return { ...base, transform: "rotate(-58deg)", opacity: 0, transition: "none", zIndex: 1 };
  };

  const ingStyle = (flavor: number, delay: number): React.CSSProperties => {
    const on = active === flavor && entered;
    return {
      opacity: on ? 1 : 0,
      transform: on ? "translateY(0px) scale(1)" : "translateY(46px) scale(0.72)",
      transition: on
        ? `opacity 0.7s ease ${delay}s, transform 1.05s cubic-bezier(0.22, 0.68, 0.2, 1) ${delay}s`
        : "opacity 0.4s ease, transform 0.5s ease",
    };
  };

  return (
    <section
      id="top"
      aria-label="Hero"
      style={{
        position: "relative",
        overflowX: "clip",
        minHeight: isMobile ? "auto" : "max(640px, 96svh)",
      }}
    >
      {/* Desktop: full-bleed color-field circle anchored to the right edge, bleeding
          off-screen so only the left half (reaching centre) shows; the section's
          overflow-x clips the off-screen part. (Mobile circle lives inside the stage.) */}
      {!isMobile && (
        <div
          aria-hidden="true"
          style={{
            position: "absolute",
            top: "50%",
            right: 0,
            transform: "translate(34%, -50%)",
            width: "min(1180px, 78vw)",
            aspectRatio: "1",
            borderRadius: "50%",
            background: FLAVORS[active].circle,
            transition: "background 1.1s ease",
            zIndex: 0,
          }}
        />
      )}

      <div
        style={{
          position: "relative",
          zIndex: 1,
          maxWidth: 1360,
          margin: "0 auto",
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          gap: "clamp(12px, 3vw, 40px)",
          padding: isMobile
            ? "clamp(84px, 12vh, 120px) clamp(20px, 4vw, 48px) 0"
            : "clamp(96px, 14vh, 140px) clamp(20px, 4vw, 48px) clamp(36px, 6vh, 70px)",
        }}
      >
        {/* Left: copy (design shows only the H1) */}
        <div style={{ flex: "1 1 430px", minWidth: 300, position: "relative", zIndex: 5 }}>
          <h1
            className="font-heading"
            style={{
              margin: 0,
              fontWeight: 700,
              fontSize: "clamp(42px, 5.2vw, 74px)",
              lineHeight: 1.06,
              letterSpacing: "-0.01em",
              color: "#2F3B28",
              textWrap: "balance",
            }}
          >
            The Ritual of{" "}
            <em style={{ fontStyle: "italic", color: FLAVORS[active].deepText, transition: "color 0.9s ease" }}>
              Natural Energy
            </em>
          </h1>
        </div>

        {/* Right: animated pouch stage */}
        <div
          onMouseEnter={() => (hover.current = true)}
          onMouseLeave={() => (hover.current = false)}
          onTouchStart={(e) => {
            touch.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
          }}
          onTouchEnd={(e) => {
            if (!touch.current) return;
            const dx = e.changedTouches[0].clientX - touch.current.x;
            const dy = e.changedTouches[0].clientY - touch.current.y;
            touch.current = null;
            if (Math.abs(dx) > 42 && Math.abs(dx) > Math.abs(dy) * 1.4) {
              go(dx < 0 ? (active + 1) % 3 : (active + 2) % 3);
            }
          }}
          style={{
            flex: "1 1 460px",
            minWidth: 280,
            position: "relative",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            minHeight: isMobile ? 470 : "clamp(440px, 52vw, 660px)",
            // Mobile: bleed the stage to the viewport edges (cancel the wrapper's side
            // padding) and clip the circle at those edges + the marquee below, so the
            // circle fills the width and reaches the bar with no cream gutters.
            margin: isMobile ? "0 calc(-1 * clamp(20px, 4vw, 48px))" : undefined,
            overflow: isMobile ? "hidden" : "visible",
          }}
        >
          {/* mobile color-field circle — top edge just below the pouch head; the width is
              viewport-relative so it always bleeds off both sides (phone and tablet) and
              off the bottom to the marquee. */}
          {isMobile && (
            <div
              aria-hidden="true"
              style={{
                position: "absolute",
                left: "50%",
                top: "clamp(88px, 22%, 150px)",
                transform: "translateX(-50%)",
                width: "142vw",
                aspectRatio: "1",
                borderRadius: "50%",
                background: FLAVORS[active].circle,
                transition: "background 1.1s ease",
                zIndex: 0,
              }}
            />
          )}

          {/* ground shadow */}
          <div
            aria-hidden="true"
            style={{
              position: "absolute",
              left: "50%",
              bottom: isMobile ? "12%" : "6%",
              transform: "translateX(-50%)",
              width: "52%",
              height: 32,
              background: "radial-gradient(ellipse at center, rgba(24,32,16,0.26), transparent 68%)",
              filter: "blur(7px)",
            }}
          />

          {/* ingredients — behind the pouch */}
          {ING_BEHIND.map((ing, i) => (
            <div
              key={ing.src}
              aria-hidden="true"
              style={{
                position: "absolute",
                // Mobile/tablet: anchor to the packet's left edge (packet is centred, so
                // % from the viewport drifts). right = 50% + packet-half-width + small gap.
                left: isMobile ? "auto" : ing.left,
                right: isMobile ? "calc(50% + 118px)" : "auto",
                bottom: isMobile ? "17%" : ing.bottom,
                width: isMobile ? "clamp(62px, 17vw, 92px)" : ing.width,
                pointerEvents: "none",
                ...ingStyle(i, 0.35),
              }}
            >
              <div style={{ animation: ing.anim }}>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={ing.src}
                  alt={ing.alt}
                  draggable={false}
                  style={{ width: "100%", transform: `rotate(${ing.rotate})`, filter: "drop-shadow(0 10px 14px rgba(24,32,16,0.18))" }}
                />
              </div>
            </div>
          ))}

          {/* floating pouch stack — pushed up a bit on mobile so it crosses the circle */}
          <div
            style={{
              position: "relative",
              height: "clamp(360px, 44vw, 580px)",
              aspectRatio: "0.62",
              animation: "mtsa-float 6.5s ease-in-out infinite",
              marginBottom: isMobile ? 44 : 0,
              zIndex: 2,
            }}
          >
            {FLAVORS.map((f, i) => (
              <div key={f.pouch} style={pouchStyle(i)}>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={f.pouch}
                  alt={f.pouchAlt}
                  draggable={false}
                  style={{ height: "100%", width: "auto", userSelect: "none", filter: "drop-shadow(0 24px 34px rgba(24,32,16,0.22))" }}
                />
              </div>
            ))}
          </div>

          {/* ingredients — in front of the pouch */}
          {ING_FRONT.map((ing, i) => (
            <div
              key={ing.src}
              aria-hidden="true"
              style={{
                position: "absolute",
                // Mobile/tablet: anchor to the packet's right edge (left = 50% + half + gap).
                left: isMobile ? "calc(50% + 118px)" : "auto",
                right: isMobile ? "auto" : ing.right,
                bottom: isMobile ? "20%" : ing.bottom,
                width: isMobile ? "clamp(50px, 13vw, 78px)" : ing.width,
                pointerEvents: "none",
                zIndex: 3,
                ...ingStyle(i, 0.55),
              }}
            >
              <div style={{ animation: ing.anim }}>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={ing.src}
                  alt={ing.alt}
                  draggable={false}
                  style={{ width: "100%", transform: `rotate(${ing.rotate})`, filter: "drop-shadow(0 10px 14px rgba(24,32,16,0.2))" }}
                />
              </div>
            </div>
          ))}

          {/* flavor dots — desktop only (mobile switches by swipe) */}
          {!isMobile && (
          <div
            role="tablist"
            aria-label="Choose a blend"
            style={{
              position: "absolute",
              bottom: "8px",
              left: "50%",
              transform: "translateX(-50%)",
              display: "flex",
              gap: 8,
              zIndex: 6,
            }}
          >
            {FLAVORS.map((f, i) => (
              <button
                key={f.pouch}
                type="button"
                role="tab"
                aria-selected={i === active}
                aria-label={f.pouchAlt}
                onClick={() => go(i)}
                style={{
                  width: i === active ? 26 : 9,
                  height: 9,
                  padding: 0,
                  border: "none",
                  borderRadius: 999,
                  cursor: "pointer",
                  background: i === active ? "#2F3B28" : "rgba(47,59,40,0.28)",
                  transition: "all 0.3s ease",
                }}
              />
            ))}
          </div>
          )}
        </div>
      </div>
    </section>
  );
}
