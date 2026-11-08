"use client";

import { useMemo, useState } from "react";
import { ProductCard, type ProductCardData } from "@/components/product/ProductCard";
import { collection } from "@/lib/stubs/home-desktop";

const pad = (n: number) => `${n < 10 ? "0" : ""}${n}`;

/** Desktop "Shop the collection": category tabs (derived from the products' categories)
 *  filter the grid client-side; the count updates. Falls back to the design collection. */
export function CategoryFilter({ products = collection }: { products?: ProductCardData[] }) {
  const tabs = useMemo(
    () => ["All", ...Array.from(new Set(products.map((p) => p.category)))],
    [products],
  );
  const [active, setActive] = useState("All");
  const visible = active === "All" ? products : products.filter((p) => p.category === active);

  return (
    <div className="dk-wrap">
      <div className="dk-section-head reveal">
        <h2>Shop the collection</h2>
        <span className="count">
          {pad(visible.length)} item{visible.length === 1 ? "" : "s"}
        </span>
      </div>

      <nav className="cat-tabs dk-cattabs reveal" aria-label="Filter by category">
        {tabs.map((tab) => (
          <button
            key={tab}
            className={`cat-tab${active === tab ? " is-active" : ""}`}
            type="button"
            aria-pressed={active === tab}
            onClick={() => setActive(tab)}
          >
            {tab}
          </button>
        ))}
      </nav>

      <section className="product-grid dk-grid" data-quickadd="off" aria-label="Catalogue">
        {visible.map((product) => (
          <ProductCard key={`${product.name}-${product.href}`} product={product} />
        ))}
      </section>
    </div>
  );
}
