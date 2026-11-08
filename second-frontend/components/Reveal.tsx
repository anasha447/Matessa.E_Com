"use client";

import { useEffect } from "react";

/** Scroll-reveal: adds `is-in` to every `.reveal` element as it enters the viewport
 *  (the design's CSS fades/translates it in). Renders nothing. Mount once per page.
 *  Under prefers-reduced-motion the CSS keeps `.reveal` fully visible anyway. */
export function Reveal() {
  useEffect(() => {
    const els = Array.from(document.querySelectorAll<HTMLElement>(".reveal"));
    if (els.length === 0) return;

    const io = new IntersectionObserver(
      (entries, observer) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-in");
            observer.unobserve(entry.target);
          }
        }
      },
      { rootMargin: "0px 0px -10% 0px", threshold: 0.05 },
    );

    els.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);

  return null;
}
