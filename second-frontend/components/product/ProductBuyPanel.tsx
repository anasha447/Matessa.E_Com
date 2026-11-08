"use client";

import { useState } from "react";
import { HeartIcon } from "@/components/ui/icons";
import { AddToCart } from "./AddToCart";
import type { ProductVariantView } from "@/lib/server/product";

/** Size selector + add-to-cart, sharing the selected-variant state. On mobile the CTA
 *  is the fixed sticky bar; on desktop (sticky=false) it's an inline row in the column. */
export function ProductBuyPanel({
  variants,
  sticky = true,
}: {
  variants: ProductVariantView[];
  sticky?: boolean;
}) {
  const hasSizes = variants.some((v) => v.size);
  const firstAvailable = variants.find((v) => v.available)?.id ?? null;
  const [selected, setSelected] = useState<number | null>(firstAvailable);

  const sizeSection = hasSizes && (
    <section className="pdp-section">
      <div className="pdp-section-head">
        <h2>Select size</h2>
        <a href="#">Size chart</a>
      </div>
      <div className="size-row">
        {variants.map((v) => (
          <button
            key={v.id}
            type="button"
            className={`size-chip${selected === v.id ? " is-selected" : ""}${
              !v.available ? " is-out" : ""
            }`}
            disabled={!v.available}
            onClick={() => setSelected(v.id)}
          >
            {v.size}
          </button>
        ))}
      </div>
    </section>
  );

  const cta = (
    <>
      <button className="save-btn" type="button" aria-label="Save to wishlist">
        <HeartIcon size={20} />
      </button>
      <AddToCart variantId={selected} disabled={variants.length === 0} />
    </>
  );

  return (
    <>
      {sizeSection}
      <div className={sticky ? "sticky-cta" : "pdp-dk-cta"}>{cta}</div>
    </>
  );
}
