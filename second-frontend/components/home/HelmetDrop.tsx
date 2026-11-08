/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ChevronLeftIcon, ChevronRightIcon } from "@/components/ui/icons";
import { dropItems } from "@/lib/stubs/home";

const pad = (n: number) => `${n < 10 ? "0" : ""}${n}`;

/** Featured-helmet "drop": tap-through carousel with per-helmet color glow.
 *  Auto-cycles every 3.5s, pauses 8s after any interaction, off under reduced-motion. */
export function HelmetDrop() {
  const total = dropItems.length;
  const [current, setCurrent] = useState(0);
  const pauseUntil = useRef(0);

  const userGo = (next: number) => {
    pauseUntil.current = Date.now() + 8000;
    setCurrent(((next % total) + total) % total);
  };

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const id = window.setInterval(() => {
      if (Date.now() < pauseUntil.current) return;
      setCurrent((c) => (c + 1) % total);
    }, 3500);
    return () => window.clearInterval(id);
  }, [total]);

  const itemClass = (i: number) =>
    i === current
      ? "drop-item is-active"
      : i < current
        ? "drop-item is-above"
        : "drop-item is-below";

  const onStageClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if ((e.target as HTMLElement).closest("a")) return;
    userGo(current + 1);
  };

  return (
    <section className="drop" aria-label="Featured helmet drop">
      <div className="drop-panel">
        {dropItems.map((item, i) => (
          <div
            key={item.glow}
            className={`drop-glow${i === current ? " is-active" : ""}`}
            data-c={item.glow}
          />
        ))}

        <div className="drop-head">
          <span className="drop-kicker">{"// This week's drop"}</span>
          <span className="drop-kicker">
            {pad(current + 1)} / {pad(total)}
          </span>
        </div>

        {/* Tap anywhere to advance; arrows/dots below are the accessible controls. */}
        <div className="drop-stage" onClick={onStageClick}>
          {dropItems.map((item, i) => (
            <article key={item.sku} className={itemClass(i)}>
              <div className="drop-media">
                <img src={item.image} alt={item.alt} />
              </div>
              <div className="drop-info">
                <span className="sku">{item.sku}</span>
                <h3>{item.name}</h3>
                <p className="tagline">{item.tagline}</p>
                <span className="price">${item.price}</span>
                <Link className="view" href={item.href}>
                  View helmet →
                </Link>
              </div>
            </article>
          ))}
        </div>

        <div className="drop-controls">
          <button
            className="drop-arrow"
            type="button"
            aria-label="Previous helmet"
            onClick={() => userGo(current - 1)}
          >
            <ChevronLeftIcon />
          </button>
          <div className="drop-dots">
            {dropItems.map((item, i) => (
              <button
                key={item.sku}
                className={`drop-dot${i === current ? " is-active" : ""}`}
                type="button"
                aria-label={`Helmet ${i + 1}`}
                onClick={() => userGo(i)}
              />
            ))}
          </div>
          <button
            className="drop-arrow"
            type="button"
            aria-label="Next helmet"
            onClick={() => userGo(current + 1)}
          >
            <ChevronRightIcon />
          </button>
        </div>
      </div>
    </section>
  );
}
