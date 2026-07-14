"use client";

import { useEffect, useState } from "react";
import { SearchIcon } from "@/components/ui/icons";
import { ProductCard, type ProductCardData } from "@/components/product/ProductCard";

const RECENT_KEY = "ad-recent-searches";

const ClockIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
    <path d="M3 3v5h5" />
    <path d="M12 7v5l3 3" />
  </svg>
);

/** Client-side product search over the fetched catalog (no backend search endpoint).
 *  Empty query → recent searches (localStorage) + browse grid; typing → live results. */
export function SearchView({ products }: { products: ProductCardData[] }) {
  const [q, setQ] = useState("");
  const [recent, setRecent] = useState<string[]>([]);

  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
      // Load persisted recent searches on mount.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      if (Array.isArray(saved)) setRecent(saved);
    } catch {
      /* ignore */
    }
  }, []);

  const query = q.trim().toLowerCase();
  const results = query
    ? products.filter(
        (p) =>
          p.name.toLowerCase().includes(query) || p.category.toLowerCase().includes(query),
      )
    : [];

  const saveRecent = (term: string) => {
    const t = term.trim();
    if (!t) return;
    const next = [t, ...recent.filter((r) => r.toLowerCase() !== t.toLowerCase())].slice(0, 6);
    setRecent(next);
    localStorage.setItem(RECENT_KEY, JSON.stringify(next));
  };

  const clearRecent = () => {
    setRecent([]);
    localStorage.removeItem(RECENT_KEY);
  };

  return (
    <>
      <div className="search-head">
        <div className="input-wrap">
          <SearchIcon size={20} />
          <input
            className="text-input"
            type="search"
            placeholder="Search helmets, gear, art…"
            aria-label="Search products"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") saveRecent(q);
            }}
            autoFocus
          />
        </div>
      </div>

      {query ? (
        results.length > 0 ? (
          <>
            <div className="section-heading">
              <h2>Results</h2>
              <span>{results.length}</span>
            </div>
            <section className="product-grid" aria-label="Search results">
              {results.map((p) => (
                <ProductCard key={p.href} product={p} />
              ))}
            </section>
          </>
        ) : (
          <p className="empty-note">No matches for “{q}”. Try another term.</p>
        )
      ) : (
        <>
          {recent.length > 0 && (
            <>
              <div className="recent-head">
                <h2>Recent searches</h2>
                <button type="button" onClick={clearRecent}>
                  Clear
                </button>
              </div>
              <div className="row-group">
                {recent.map((r) => (
                  <button key={r} className="row-item" type="button" onClick={() => setQ(r)}>
                    <ClockIcon />
                    <span className="grow">{r}</span>
                  </button>
                ))}
              </div>
            </>
          )}
          <div className="section-heading">
            <h2>Browse the collection</h2>
          </div>
          <section className="product-grid" aria-label="Browse products">
            {products.slice(0, 8).map((p) => (
              <ProductCard key={p.href} product={p} />
            ))}
          </section>
        </>
      )}
    </>
  );
}
