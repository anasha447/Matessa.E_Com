"use client";

import { useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import type { OrderResponse, PaymentMethod } from "@/lib/api";
import { useCart, clearGuestCart } from "@/components/cart/CartContext";
import {
  cartFingerprint,
  checkoutIdempotencyKey,
  clearCheckoutIdempotencyKey,
} from "@/lib/checkout/idempotency";
import type { CartViewData } from "@/lib/server/cart";

const fmt = (n: number) => `$${n.toFixed(2)}`;
const pad = (n: number) => `${n < 10 ? "0" : ""}${n}`;

interface CheckoutUser {
  email: string;
  fullName: string;
}

const PAYMENTS: { key: PaymentMethod; title: string; sub: string }[] = [
  { key: "COD", title: "Cash on delivery", sub: "Pay when it arrives" },
  { key: "ONLINE", title: "Online payment", sub: "Card, Razorpay or PayPal" },
];

export function CheckoutForm({
  cart,
  user,
  initialCoupon,
  guest = false,
}: {
  cart: CartViewData;
  user: CheckoutUser | null;
  initialCoupon: string | null;
  /** Guest (no account): submits to the public guest-checkout endpoint and confirms with
   *  a tracking link instead of an account orders link. */
  guest?: boolean;
}) {
  const { refresh } = useCart();
  const [placed, setPlaced] = useState<OrderResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [email, setEmail] = useState(user?.email ?? "");
  const [phone, setPhone] = useState("");
  const [fullName, setFullName] = useState(user?.fullName ?? "");
  const [line1, setLine1] = useState("");
  const [line2, setLine2] = useState("");
  const [city, setCity] = useState("");
  const [region, setRegion] = useState("");
  const [postalCode, setPostalCode] = useState("");
  const [country, setCountry] = useState("USA");
  // Delivery method is chosen by staff after the order is placed (admin-only); default it here
  // so the order still records a sensible value that staff can change later.
  const shippingMethod = "Standard";
  const [payment, setPayment] = useState<PaymentMethod>("COD");

  const count = cart.items.reduce((s, i) => s + i.quantity, 0);
  const total = cart.subtotal;

  const placeOrder = async () => {
    setError(null);
    if (!fullName || !line1 || !city || !region || !postalCode || !country || !phone) {
      setError("Please complete all required contact and shipping fields.");
      return;
    }
    if (guest && !/^\S+@\S+\.\S+$/.test(email)) {
      setError("Please enter a valid email so we can send your order confirmation.");
      return;
    }
    setSubmitting(true);
    // Bound to this basket and held in sessionStorage, so a reload-and-resubmit returns
    // the original order instead of placing a second one. See lib/checkout/idempotency.
    const idemKey = checkoutIdempotencyKey(
      cartFingerprint(
        cart.items.map((i) => ({ productVariantId: i.productVariantId, quantity: i.quantity })),
        initialCoupon || null,
      ),
    );
    try {
      const shippingAddress = {
        recipientName: fullName,
        line1,
        line2: line2 || null,
        city,
        state: region,
        postalCode,
        country,
        phone,
      };
      const order = guest
        ? await api.orders.guestCheckout({
            guestEmail: email,
            items: cart.items.map((i) => ({ productVariantId: i.productVariantId, quantity: i.quantity })),
            shippingAddress,
            paymentMethod: payment,
            couponCode: initialCoupon || null,
            shippingMethod,
          }, idemKey)
        : await api.orders.checkout({
            shippingAddress,
            paymentMethod: payment,
            couponCode: initialCoupon || null,
            shippingMethod,
          }, idemKey);
      setPlaced(order);
      clearCheckoutIdempotencyKey();   // this purchase is done; the next one starts clean
      if (guest) clearGuestCart();
      void refresh(); // cart consumed → badge updates
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.isConflict) {
          setError("Sorry — an item just went out of stock. Please review your cart and try again.");
        } else if (e.isUnavailable) {
          setError("The store is briefly unavailable. Please try again in a moment.");
        } else if (e.isUnauthorized) {
          setError("Your session expired — please sign in again.");
        } else {
          setError(e.message || "Couldn't place your order. Please try again.");
        }
      } else {
        setError("Network error — please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (placed) {
    return (
      <div className="confirm">
        <div className="mark" />
        <h2>Order placed</h2>
        <div className="order-no">{placed.orderNumber}</div>
        {guest ? (
          <>
            <p>
              Thanks! We&apos;ve emailed your confirmation to <strong>{email}</strong>. Save your
              order number — you can track your order anytime with it.
            </p>
            <div style={{ display: "flex", gap: 10 }}>
              <Link
                className="btn btn-outline"
                href={`/track?number=${encodeURIComponent(placed.orderNumber)}`}
              >
                Track order
              </Link>
              <Link className="btn btn-primary" href="/">
                Back to home
              </Link>
            </div>
          </>
        ) : (
          <>
            <p>Your gear is getting ready for the road. Track it anytime from your orders.</p>
            <div style={{ display: "flex", gap: 10 }}>
              <Link className="btn btn-outline" href="/orders">
                View orders
              </Link>
              <Link className="btn btn-primary" href="/">
                Back to home
              </Link>
            </div>
          </>
        )}
      </div>
    );
  }

  return (
    <>
      <div className="co-title-bar">
        <h1>Checkout</h1>
        <span className="meta">
          {pad(count)} item{count === 1 ? "" : "s"}
        </span>
      </div>

      <div className="co-layout">
        <div className="co-main">
          <section className="co-section">
            <h2>
              Contact <span className="step">01 / 03</span>
            </h2>
            <div className="field">
              <label htmlFor="coEmail">Email</label>
              <input id="coEmail" className="text-input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
            </div>
            <div className="field" style={{ marginBottom: 0 }}>
              <label htmlFor="coPhone">Phone</label>
              <input id="coPhone" className="text-input" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+1 (555) 000-0000" autoComplete="tel" />
            </div>
          </section>

          <section className="co-section">
            <h2>
              Shipping address <span className="step">02 / 03</span>
            </h2>
            <div className="field">
              <label htmlFor="coName">Full name</label>
              <input id="coName" className="text-input" value={fullName} onChange={(e) => setFullName(e.target.value)} autoComplete="name" />
            </div>
            <div className="field">
              <label htmlFor="coAddr">Street address</label>
              <input id="coAddr" className="text-input" value={line1} onChange={(e) => setLine1(e.target.value)} placeholder="Street and number" autoComplete="street-address" />
            </div>
            <div className="field">
              <label htmlFor="coAddr2">Apartment, suite (optional)</label>
              <input id="coAddr2" className="text-input" value={line2} onChange={(e) => setLine2(e.target.value)} autoComplete="address-line2" />
            </div>
            <div className="field-row">
              <div className="field" style={{ marginBottom: 0 }}>
                <label htmlFor="coCity">City</label>
                <input id="coCity" className="text-input" value={city} onChange={(e) => setCity(e.target.value)} autoComplete="address-level2" />
              </div>
              <div className="field" style={{ marginBottom: 0 }}>
                <label htmlFor="coState">State</label>
                <input id="coState" className="text-input" value={region} onChange={(e) => setRegion(e.target.value)} autoComplete="address-level1" />
              </div>
            </div>
            <div className="field-row" style={{ marginTop: 12 }}>
              <div className="field" style={{ marginBottom: 0 }}>
                <label htmlFor="coZip">ZIP code</label>
                <input id="coZip" className="text-input" inputMode="numeric" value={postalCode} onChange={(e) => setPostalCode(e.target.value)} autoComplete="postal-code" />
              </div>
              <div className="field" style={{ marginBottom: 0 }}>
                <label htmlFor="coCountry">Country</label>
                <input id="coCountry" className="text-input" value={country} onChange={(e) => setCountry(e.target.value)} autoComplete="country-name" />
              </div>
            </div>
          </section>

          <section className="co-section">
            <h2>
              Payment <span className="step">03 / 03</span>
            </h2>
            {PAYMENTS.map((p) => (
              <button key={p.key} type="button" className={`opt-card${payment === p.key ? " is-selected" : ""}`} onClick={() => setPayment(p.key)}>
                <span className="radio" />
                <span className="opt-body">
                  <span className="opt-title">{p.title}</span>
                  <span className="opt-sub">{p.sub}</span>
                </span>
              </button>
            ))}
            {payment === "ONLINE" && (
              <p className="opt-sub" style={{ marginTop: 12 }}>
                You&apos;ll be redirected to complete payment securely. Payment is simulated in this build.
              </p>
            )}
          </section>
        </div>

        <aside className="co-aside">
          <section className="summary co-summary" aria-label="Order summary">
            <h2>Order summary</h2>
            <div className="co-items">
              {cart.items.map((i) => (
                <div className="co-item" key={i.id}>
                  <span>
                    {i.productName} × {i.quantity}
                  </span>
                  <span className="pr">{fmt(i.lineTotal)}</span>
                </div>
              ))}
            </div>
            <div className="summary-row">
              <span>Subtotal</span>
              <strong>{fmt(cart.subtotal)}</strong>
            </div>
            <div className="summary-row">
              <span>Shipping</span>
              <strong>Free</strong>
            </div>
            {initialCoupon && (
              <div className="summary-row">
                <span>Coupon</span>
                <strong>{initialCoupon}</strong>
              </div>
            )}
            <div className="summary-row total">
              <span>Total</span>
              <span className="amount">{fmt(total)}</span>
            </div>
          </section>

          {error && (
            <p className="co-error" role="alert">
              {error}
            </p>
          )}

          <div className="place-bar">
            <div className="total-chip">
              <small>Total</small>
              <span>{fmt(total)}</span>
            </div>
            <button className="btn btn-primary" type="button" onClick={placeOrder} disabled={submitting}>
              {submitting ? "Placing…" : "Place order"}
            </button>
          </div>
        </aside>
      </div>
    </>
  );
}
