/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect } from "react";
import Link from "next/link";
import { CloseIcon, MinusIcon, PlusIcon } from "@/components/ui/icons";
import { CART_PLACEHOLDER, useCart } from "./CartContext";

// Null-tolerant: an unavailable line (catalog briefly down, API.md §4.9) comes back
// with a null price — render a dash rather than crashing on n.toFixed.
const fmt = (n: number | null | undefined) => (typeof n === "number" ? `$${n.toFixed(2)}` : "—");
const pad = (n: number) => `${n < 10 ? "0" : ""}${n}`;

function variantLabel(sku: string | null | undefined): string | null {
  if (!sku) return null;
  const m = sku.match(/-(XS|S|M|L|XL|XXL)$/i);
  if (m) return `Size ${m[1].toUpperCase()}`;
  if (/-OS$/i.test(sku)) return "One size";
  return null;
}

/** Slide-in cart drawer (full-page on mobile, ~1/3 on desktop). Global; driven by the
 *  cart context. Add-to-cart opens it automatically. */
export function CartDrawer() {
  const { cart, images, count, subtotal, isOpen, close, setQty, removeItem } = useCart();
  const items = cart?.items ?? [];

  // Lock body scroll while open; close on Escape.
  useEffect(() => {
    if (!isOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && close();
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener("keydown", onKey);
    };
  }, [isOpen, close]);

  return (
    <>
      <div
        className={`drawer-scrim${isOpen ? " is-open" : ""}`}
        aria-hidden="true"
        onClick={close}
      />
      <aside
        className={`cart-drawer${isOpen ? " is-open" : ""}`}
        aria-label="Shopping cart"
        aria-hidden={!isOpen}
      >
        <div className="cd-head">
          <h2>
            Cart
            <span className="cd-count">
              {pad(count)} item{count === 1 ? "" : "s"}
            </span>
          </h2>
          <button className="cd-close" type="button" aria-label="Close cart" onClick={close}>
            <CloseIcon size={20} />
          </button>
        </div>

        <div className="cd-items">
          {items.length === 0 ? (
            <p className="cd-empty">Your cart is empty. Find something worth the ride.</p>
          ) : (
            items.map((item) => (
              <div className="cd-item" key={item.id}>
                <img src={images[item.productVariantId] ?? CART_PLACEHOLDER} alt={item.productName} />
                <div className="cd-info">
                  <span className="cd-name">{item.productName}</span>
                  <div className="cd-meta">
                    <div className="qty-stepper" aria-label="Quantity">
                      <button
                        type="button"
                        aria-label="Decrease quantity"
                        onClick={() => setQty(item.id, item.quantity - 1)}
                      >
                        <MinusIcon size={14} />
                      </button>
                      <span className="qty">{item.quantity}</span>
                      <button
                        type="button"
                        aria-label="Increase quantity"
                        disabled={item.quantity >= item.stockQuantity}
                        onClick={() => setQty(item.id, item.quantity + 1)}
                      >
                        <PlusIcon size={14} />
                      </button>
                    </div>
                    {variantLabel(item.sku) && <span className="cd-variant">{variantLabel(item.sku)}</span>}
                  </div>
                </div>
                <div className="cd-right">
                  <span className="cd-price">{fmt(item.lineTotal)}</span>
                  {!item.available && <span className="ci-warn">Unavailable</span>}
                  <button className="cd-remove" type="button" onClick={() => removeItem(item.id)}>
                    Remove
                  </button>
                </div>
              </div>
            ))
          )}
        </div>

        {items.length > 0 && (
          <div className="cd-foot">
            <div className="cd-sub">
              <span>Subtotal</span>
              <strong>{fmt(subtotal)}</strong>
            </div>
            <div className="cd-actions">
              <Link className="btn btn-primary" href="/checkout" onClick={close}>
                Checkout
              </Link>
            </div>
          </div>
        )}
      </aside>
    </>
  );
}
