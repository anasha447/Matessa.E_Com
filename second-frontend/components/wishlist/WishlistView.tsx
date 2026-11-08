/* eslint-disable @next/next/no-img-element */
"use client";

import { useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import type { WishlistEntry } from "@/lib/server/wishlist";

/** Wishlist grid with optimistic remove; brand empty-state when nothing is saved. */
export function WishlistView({ initial }: { initial: WishlistEntry[] }) {
  const [items, setItems] = useState(initial);

  const remove = async (id: number) => {
    setItems((xs) => xs.filter((x) => x.wishlistItemId !== id));
    await api.wishlist.removeItem(id).catch(() => {});
  };

  if (items.length === 0) {
    return (
      <section className="empty-state" aria-label="Empty wishlist">
        <div className="wing-big" />
        <h2>Nothing saved yet</h2>
        <p>Spot something you like? Tap the heart and it&apos;ll wait for you here.</p>
        <Link className="btn btn-primary" href="/shop">
          Continue shopping
        </Link>
      </section>
    );
  }

  return (
    <>
      <div className="section-heading">
        <h2>Wishlist</h2>
        <span>
          {items.length} saved
        </span>
      </div>
      <section className="product-grid" aria-label="Wishlist">
        {items.map(({ wishlistItemId, product }) => (
          <article className="product-card" key={wishlistItemId}>
            <div className="pc-media">
              <button
                className="wish-remove"
                type="button"
                aria-label={`Remove ${product.name} from wishlist`}
                onClick={() => remove(wishlistItemId)}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M18 6 6 18M6 6l12 12" />
                </svg>
              </button>
              <Link href={product.href} aria-label={`View ${product.name}`}>
                <img src={product.image} alt={product.alt} />
              </Link>
            </div>
            <div className="pc-body">
              <span className="pc-cat">{product.category}</span>
              <h3 className="pc-name">{product.name}</h3>
              <div className="pc-row">
                <span className="pc-price">${product.price}</span>
              </div>
            </div>
          </article>
        ))}
      </section>
    </>
  );
}
