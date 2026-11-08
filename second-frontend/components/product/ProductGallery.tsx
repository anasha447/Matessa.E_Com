/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useRef, useState } from "react";
import type { ProductImageView } from "@/lib/server/product";

/** Scroll-snap photo carousel with dots (native swipe; dots reflect + drive scroll). */
export function ProductGallery({ images }: { images: ProductImageView[] }) {
  const trackRef = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);

  useEffect(() => {
    const track = trackRef.current;
    if (!track) return;
    const onScroll = () => {
      if (track.clientWidth > 0) setActive(Math.round(track.scrollLeft / track.clientWidth));
    };
    track.addEventListener("scroll", onScroll, { passive: true });
    return () => track.removeEventListener("scroll", onScroll);
  }, []);

  const goTo = (i: number) => {
    const track = trackRef.current;
    if (track) track.scrollTo({ left: i * track.clientWidth, behavior: "smooth" });
  };

  return (
    <section className="gallery" aria-label="Product photos">
      <div className="gallery-track" ref={trackRef}>
        {images.map((img, i) => (
          <div className="gallery-slide" key={i}>
            <img src={img.url} alt={img.alt} />
          </div>
        ))}
      </div>
      {images.length > 1 && (
        <div className="gallery-dots">
          {images.map((_, i) => (
            <button
              key={i}
              type="button"
              className={`dot${i === active ? " is-active" : ""}`}
              aria-label={`Go to photo ${i + 1}`}
              onClick={() => goTo(i)}
            />
          ))}
        </div>
      )}
    </section>
  );
}
