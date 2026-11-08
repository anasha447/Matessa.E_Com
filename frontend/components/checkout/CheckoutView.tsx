"use client";

// Unified checkout (guest + authed) driven by the cart context. Guests supply an email
// and submit to the public guest-checkout endpoint; authed users submit the server cart.
// Keeps the Indian-state dropdown and the ₹ order summary; confirmation shows the MA
// order number with Track / Home CTAs.

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import type { OrderResponse, PaymentMethod } from "@/lib/api";
import { useCart, clearGuestCart } from "@/components/cart/CartContext";
import {
  cartFingerprint,
  checkoutIdempotencyKey,
  clearCheckoutIdempotencyKey,
} from "@/lib/checkout/idempotency";
import { INDIAN_STATES_AND_UTS } from "@/lib/constants/indianStates";
import { formatMoney } from "@/lib/utils/money";

const PAYMENTS: { key: PaymentMethod; title: string; sub: string }[] = [
  { key: "COD", title: "Cash on delivery", sub: "Pay when it arrives" },
  { key: "ONLINE", title: "Online payment", sub: "Card, UPI or Razorpay" },
];

export function CheckoutView({
  user,
  initialCoupon,
  guest,
}: {
  user: { email: string; fullName: string } | null;
  initialCoupon: string | null;
  guest: boolean;
}) {
  const router = useRouter();
  const { cart, subtotal, count, refresh } = useCart();
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
  const [country] = useState("India");
  const shippingMethod = "Standard";
  const [payment, setPayment] = useState<PaymentMethod>("COD");

  // Coupon: a stateless preview (api.coupons.validate) computes the discount; the code
  // is passed to the order so the server re-validates and redeems it authoritatively.
  const [coupon, setCoupon] = useState(initialCoupon ?? "");
  const [applied, setApplied] = useState<{ code: string; discount: number } | null>(null);
  const [couponMsg, setCouponMsg] = useState<string | null>(null);
  const [couponBusy, setCouponBusy] = useState(false);

  const items = cart?.items ?? [];
  const discount = applied?.discount ?? 0;
  const total = Math.max(0, subtotal - discount);

  const applyCoupon = async () => {
    const code = coupon.trim();
    if (!code) return;
    setCouponBusy(true);
    setCouponMsg(null);
    try {
      const res = await api.coupons.validate({ code, subtotal });
      if (res.valid) {
        setApplied({ code: res.code, discount: res.discountAmount });
        setCouponMsg(`Applied — you saved ${formatMoney(res.discountAmount)}.`);
      } else {
        setApplied(null);
        setCouponMsg(res.message || "That coupon isn't valid for this order.");
      }
    } catch (e) {
      // If the live preview isn't available to this user, still accept the code — the
      // server re-validates and applies it authoritatively at checkout.
      if (e instanceof ApiError && e.isUnauthorized) {
        setApplied({ code, discount: 0 });
        setCouponMsg("Code added — the discount is applied at checkout.");
      } else {
        setApplied(null);
        setCouponMsg("Couldn't check that coupon — please try again.");
      }
    } finally {
      setCouponBusy(false);
    }
  };

  const removeCoupon = () => {
    setApplied(null);
    setCoupon("");
    setCouponMsg(null);
  };

  const placeOrder = async () => {
    setError(null);
    if (!fullName || !line1 || !city || !region || !postalCode || !phone) {
      setError("Please complete all required contact and shipping fields.");
      return;
    }
    if (guest && !/^\S+@\S+\.\S+$/.test(email)) {
      setError("Please enter a valid email so we can send your order confirmation.");
      return;
    }
    if (items.length === 0) {
      setError("Your cart is empty.");
      return;
    }
    setSubmitting(true);
    // Bound to this basket and held in sessionStorage, so a reload-and-resubmit returns
    // the original order instead of placing a second one. See lib/checkout/idempotency.
    const idemKey = checkoutIdempotencyKey(
      cartFingerprint(
        items.map((i) => ({ productVariantId: i.productVariantId, quantity: i.quantity })),
        applied?.code ?? null,
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
        ? await api.orders.guestCheckout(
            {
              guestEmail: email,
              items: items.map((i) => ({ productVariantId: i.productVariantId, quantity: i.quantity })),
              shippingAddress,
              paymentMethod: payment,
              couponCode: applied?.code ?? null,
              shippingMethod,
            },
            idemKey,
          )
        : await api.orders.checkout(
            { shippingAddress, paymentMethod: payment, couponCode: applied?.code ?? null, shippingMethod },
            idemKey,
          );
      setPlaced(order);
      clearCheckoutIdempotencyKey();   // this purchase is done; the next one starts clean
      if (guest) clearGuestCart();
      void refresh();
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.isConflict) setError("Sorry — an item just went out of stock. Please review your cart and try again.");
        else if (e.isUnavailable) setError("The store is briefly unavailable. Please try again in a moment.");
        else if (e.isUnauthorized) setError("Your session expired — please sign in again.");
        else setError(e.message || "Couldn't place your order. Please try again.");
      } else {
        setError("Network error — please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (placed) {
    return (
      <div className="mx-auto flex max-w-lg flex-col items-center px-6 py-20 text-center">
        <div className="mb-5 grid h-16 w-16 place-items-center rounded-full bg-green text-2xl text-white">✓</div>
        <h1 className="font-heading text-3xl font-bold text-darkgreen">Order placed</h1>
        <p className="mt-2 font-body text-lg font-bold tracking-wide text-green">{placed.orderNumber}</p>
        <p className="mt-3 text-gray-600">
          {guest ? (
            <>
              Thanks! We&apos;ve emailed your confirmation to <strong>{email}</strong>. Save your order number — you can
              track it anytime.
            </>
          ) : (
            <>Your blend is getting ready. Track it anytime from your orders.</>
          )}
        </p>
        <div className="mt-8 flex gap-3">
          <Link
            href={guest ? `/track?number=${encodeURIComponent(placed.orderNumber)}` : "/orders"}
            className="rounded-full border-2 border-darkgreen px-6 py-3 font-body font-bold text-darkgreen transition-colors hover:border-orange hover:text-orange"
          >
            {guest ? "Track order" : "View orders"}
          </Link>
          <Link href="/" className="rounded-full bg-green px-6 py-3 font-body font-bold text-white transition-colors hover:bg-orange">
            Back to home
          </Link>
        </div>
      </div>
    );
  }

  const field = "w-full rounded-xl border border-gray-300 bg-white px-4 py-3 font-body outline-none focus:border-green";

  return (
    <div className="mx-auto max-w-[1000px] px-5 py-10 nav:px-8">
      <h1 className="mb-8 font-heading text-3xl font-bold text-darkgreen md:text-4xl">
        Checkout <span className="font-body text-lg font-normal text-gray-500">{count} item{count === 1 ? "" : "s"}</span>
      </h1>

      <div className="grid gap-10 nav:grid-cols-[1fr_340px]">
        <div className="space-y-8">
          {/* Contact */}
          <section>
            <h2 className="mb-4 font-heading text-xl font-bold text-darkgreen">Contact</h2>
            <div className="grid gap-3 sm:grid-cols-2">
              <input className={field} type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" aria-label="Email" />
              <input className={field} type="tel" placeholder="+91 98765 43210" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" aria-label="Phone" />
            </div>
          </section>

          {/* Shipping */}
          <section>
            <h2 className="mb-4 font-heading text-xl font-bold text-darkgreen">Shipping address</h2>
            <div className="grid gap-3">
              <input className={field} placeholder="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} autoComplete="name" aria-label="Full name" />
              <input className={field} placeholder="Street address" value={line1} onChange={(e) => setLine1(e.target.value)} autoComplete="address-line1" aria-label="Street address" />
              <input className={field} placeholder="Apartment, suite (optional)" value={line2} onChange={(e) => setLine2(e.target.value)} autoComplete="address-line2" aria-label="Apartment, suite" />
              <div className="grid gap-3 sm:grid-cols-2">
                <input className={field} placeholder="City" value={city} onChange={(e) => setCity(e.target.value)} autoComplete="address-level2" aria-label="City" />
                <select className={field} value={region} onChange={(e) => setRegion(e.target.value)} autoComplete="address-level1" aria-label="State">
                  <option value="" disabled>
                    Select state
                  </option>
                  {INDIAN_STATES_AND_UTS.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <input className={field} inputMode="numeric" placeholder="PIN code" value={postalCode} onChange={(e) => setPostalCode(e.target.value)} autoComplete="postal-code" aria-label="PIN code" />
                <input className={`${field} bg-gray-50`} value={country} readOnly aria-label="Country" />
              </div>
            </div>
          </section>

          {/* Payment */}
          <section>
            <h2 className="mb-4 font-heading text-xl font-bold text-darkgreen">Payment</h2>
            <div className="grid gap-3 sm:grid-cols-2">
              {PAYMENTS.map((p) => (
                <button
                  key={p.key}
                  type="button"
                  onClick={() => setPayment(p.key)}
                  aria-pressed={payment === p.key}
                  className={`rounded-xl border-2 p-4 text-left transition-colors ${
                    payment === p.key ? "border-green bg-[#E8F3E8]" : "border-gray-200 hover:border-orange"
                  }`}
                >
                  <span className="block font-body font-bold text-darkgreen">{p.title}</span>
                  <span className="block font-body text-sm text-gray-500">{p.sub}</span>
                </button>
              ))}
            </div>
          </section>
        </div>

        {/* Summary */}
        <aside className="h-fit rounded-[22px] bg-white p-6 shadow-lg nav:sticky nav:top-24">
          <h2 className="mb-4 font-heading text-lg font-bold text-darkgreen">Order summary</h2>
          <ul className="space-y-2 border-b border-gray-100 pb-3">
            {items.map((i) => (
              <li key={i.id} className="flex justify-between gap-3 font-body text-sm text-gray-600">
                <span className="truncate">
                  {i.productName} × {i.quantity}
                </span>
                <span className="whitespace-nowrap font-bold text-green">{formatMoney(i.lineTotal)}</span>
              </li>
            ))}
          </ul>

          {/* Coupon */}
          <div className="border-b border-gray-100 py-4">
            {applied ? (
              <div className="flex items-center justify-between gap-2">
                <span className="font-body text-sm font-bold text-green">
                  {applied.code} applied
                </span>
                <button type="button" onClick={removeCoupon} className="font-body text-sm text-gray-400 transition-colors hover:text-orange">
                  Remove
                </button>
              </div>
            ) : (
              <div className="flex gap-2">
                <input
                  value={coupon}
                  onChange={(e) => setCoupon(e.target.value.toUpperCase())}
                  onKeyDown={(e) => e.key === "Enter" && (e.preventDefault(), applyCoupon())}
                  placeholder="Coupon code"
                  aria-label="Coupon code"
                  className="min-w-0 flex-1 rounded-xl border border-gray-300 bg-white px-3 py-2.5 font-body text-sm uppercase outline-none focus:border-green"
                />
                <button
                  type="button"
                  onClick={applyCoupon}
                  disabled={couponBusy || !coupon.trim()}
                  className="rounded-xl bg-darkgreen px-4 py-2.5 font-body text-sm font-bold text-craemy transition-colors hover:bg-green disabled:opacity-40"
                >
                  {couponBusy ? "…" : "Apply"}
                </button>
              </div>
            )}
            {couponMsg && (
              <p className={`mt-2 font-body text-sm ${applied ? "text-green" : "text-orange"}`}>{couponMsg}</p>
            )}
          </div>

          {/* Totals */}
          <div className="space-y-1.5 pt-4 font-body text-sm">
            <div className="flex justify-between text-gray-600">
              <span>Subtotal</span>
              <span className="font-semibold text-darkgreen">{formatMoney(subtotal)}</span>
            </div>
            {discount > 0 && (
              <div className="flex justify-between text-green">
                <span>Discount</span>
                <span className="font-semibold">− {formatMoney(discount)}</span>
              </div>
            )}
          </div>
          <div className="mt-2 flex items-center justify-between border-t border-gray-100 py-4">
            <span className="font-body font-bold text-gray-700">Total</span>
            <span className="font-heading text-2xl font-bold text-green">{formatMoney(total)}</span>
          </div>
          {error && <p className="mb-3 font-body text-sm text-orange">{error}</p>}
          <button
            type="button"
            disabled={submitting}
            onClick={placeOrder}
            className="block w-full rounded-full bg-green py-3.5 text-center font-body font-bold text-white transition-colors duration-300 hover:bg-orange disabled:opacity-40"
          >
            {submitting ? "Placing order…" : "Place order"}
          </button>
        </aside>
      </div>
    </div>
  );
}
