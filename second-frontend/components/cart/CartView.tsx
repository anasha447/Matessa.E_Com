/* eslint-disable @next/next/no-img-element */
"use client";

import { useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import { MinusIcon, PlusIcon } from "@/components/ui/icons";
import { useCart, CART_PLACEHOLDER } from "@/components/cart/CartContext";
import type { CartViewData } from "@/lib/server/cart";

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

function EmptyCart() {
  return (
    <div className="empty-state">
      <div className="sad-mark" role="img" aria-label="Adventure Dose logo with drooping wings">
        <span className="sad-wing-wrap left">
          <span className="sad-wing" />
        </span>
        <span className="sad-helmet" />
        <span className="sad-wing-wrap right">
          <span className="sad-wing" />
        </span>
      </div>
      <h2>Cart&apos;s empty</h2>
      <p>Even the wings are down about it. Find something worth the ride.</p>
      <Link className="btn btn-primary" href="/shop">
        Shop helmets
      </Link>
    </div>
  );
}

export function CartView({ initial }: { initial: CartViewData }) {
  // Cart data + mutations come from the context, which backs a server cart (signed in) or a
  // client-side guest cart transparently. `initial` seeds the first paint (SSR) only.
  const { cart: ctxCart, images: ctxImages, setQty: ctxSetQty, removeItem: ctxRemove } = useCart();
  const [busy, setBusy] = useState<number | null>(null);
  const [coupon, setCoupon] = useState("");
  const [applied, setApplied] = useState<{ code: string; amount: number; message: string } | null>(null);
  const [couponMsg, setCouponMsg] = useState<string | null>(null);

  const cart: CartViewData = ctxCart
    ? {
        cartId: ctxCart.cartId,
        userId: ctxCart.userId,
        subtotal: ctxCart.subtotal,
        items: ctxCart.items.map((i) => ({ ...i, image: ctxImages[i.productVariantId] ?? CART_PLACEHOLDER })),
      }
    : initial;

  const setQty = async (id: number, qty: number) => {
    setBusy(id);
    try {
      await ctxSetQty(id, qty);
    } catch {
      /* leave state as-is on failure */
    } finally {
      setBusy(null);
    }
  };

  const remove = async (id: number) => {
    setBusy(id);
    try {
      await ctxRemove(id);
    } catch {
      /* ignore */
    } finally {
      setBusy(null);
    }
  };

  const applyCoupon = async () => {
    const code = coupon.trim();
    if (!code) return;
    setCouponMsg(null);
    try {
      const r = await api.coupons.validate({ code, subtotal: cart.subtotal });
      if (r.valid) {
        setApplied({ code: r.code, amount: r.discountAmount, message: r.message || "Coupon applied" });
      } else {
        setApplied(null);
        setCouponMsg(r.message || "That code isn't valid.");
      }
    } catch {
      setApplied(null);
      setCouponMsg("Couldn't check that code — try again.");
    }
  };

  if (cart.items.length === 0) return <EmptyCart />;

  const discount = applied?.amount ?? 0;
  const total = Math.max(0, cart.subtotal - discount);
  const count = cart.items.reduce((s, i) => s + i.quantity, 0);
  const checkoutHref = applied ? `/checkout?coupon=${encodeURIComponent(applied.code)}` : "/checkout";

  return (
    <>
      <div className="cart-title-bar">
        <h1>Cart</h1>
        <span className="meta">
          {pad(count)} item{count === 1 ? "" : "s"}
        </span>
      </div>

      <div className="cart-layout">
        <section className="cart-list" aria-label="Cart items">
          {cart.items.map((item) => (
            <article className="cart-item" key={item.id}>
              <div className="ci-media">
                <img src={item.image} alt={item.productName} />
              </div>
              <div className="ci-body">
                <span className="ci-sku">{item.sku}</span>
                <h3 className="ci-name">{item.productName}</h3>
                <div className="ci-row">
                  <div className="ci-qtygroup">
                    <div className="qty-stepper" aria-label="Quantity">
                      <button
                        type="button"
                        aria-label="Decrease quantity"
                        disabled={busy === item.id}
                        onClick={() => setQty(item.id, item.quantity - 1)}
                      >
                        <MinusIcon size={16} />
                      </button>
                      <span className="qty">{item.quantity}</span>
                      <button
                        type="button"
                        aria-label="Increase quantity"
                        disabled={busy === item.id || item.quantity >= item.stockQuantity}
                        onClick={() => setQty(item.id, item.quantity + 1)}
                      >
                        <PlusIcon size={16} />
                      </button>
                    </div>
                    {variantLabel(item.sku) && <span className="ci-variant">{variantLabel(item.sku)}</span>}
                  </div>
                  <span className="ci-price">{fmt(item.lineTotal)}</span>
                </div>
                <div className="ci-row" style={{ paddingTop: 4 }}>
                  <button
                    className="ci-remove"
                    type="button"
                    disabled={busy === item.id}
                    onClick={() => remove(item.id)}
                  >
                    Remove
                  </button>
                  {!item.available && <span className="ci-warn">Unavailable</span>}
                </div>
              </div>
            </article>
          ))}
        </section>

        <div className="cart-aside">
          <section className="summary" aria-label="Order summary">
            <h2>Order summary</h2>
            <div className="coupon-row">
              <input
                className="text-input"
                placeholder="Coupon code"
                aria-label="Coupon code"
                value={coupon}
                onChange={(e) => setCoupon(e.target.value)}
              />
              <button className="btn btn-outline" type="button" onClick={applyCoupon}>
                Apply
              </button>
            </div>
            {couponMsg && <p className="auth-error">{couponMsg}</p>}
            {applied && <p className="coupon-ok">{applied.message}</p>}
            <div className="summary-row">
              <span>Subtotal</span>
              <strong>{fmt(cart.subtotal)}</strong>
            </div>
            <div className="summary-row">
              <span>Shipping</span>
              <strong>Free</strong>
            </div>
            <div className="summary-row">
              <span>Discount</span>
              <strong>{discount > 0 ? `-${fmt(discount)}` : "—"}</strong>
            </div>
            <div className="summary-row total">
              <span>Total</span>
              <span className="amount">{fmt(total)}</span>
            </div>
          </section>
          <div className="checkout-wrap">
            <Link className="btn btn-primary btn-block" href={checkoutHref}>
              Checkout
            </Link>
            <p className="checkout-note">Taxes calculated at checkout. 30-day returns.</p>
          </div>
        </div>
      </div>
    </>
  );
}
