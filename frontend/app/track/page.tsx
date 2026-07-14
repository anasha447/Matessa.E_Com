"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { TopBar } from "@/components/layout/TopBar";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { api, ApiError } from "@/lib/api";
import type { OrderResponse, OrderStatus } from "@/lib/api";

const fmt = (n: number | null | undefined) => (typeof n === "number" ? `$${n.toFixed(2)}` : "—");

const BADGE: Record<OrderStatus, { cls: string; label: string }> = {
  PENDING: { cls: "st-paid", label: "Pending" },
  PAID: { cls: "st-paid", label: "Paid" },
  SHIPPED: { cls: "st-shipped", label: "Shipped" },
  DELIVERED: { cls: "st-delivered", label: "Delivered" },
  CANCELLED: { cls: "st-cancelled", label: "Cancelled" },
};
const RANK: Record<OrderStatus, number> = { PENDING: 0, PAID: 1, SHIPPED: 2, DELIVERED: 3, CANCELLED: -1 };

const Check = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 6 9 17l-5-5" />
  </svg>
);

function Timeline({ status }: { status: OrderStatus }) {
  if (status === "CANCELLED") {
    return (
      <div>
        <div className="tl-step is-done"><span className="tl-dot"><Check /></span><div className="tl-body"><span className="tl-label">Order placed</span></div></div>
        <div className="tl-step is-now is-fail"><span className="tl-dot" /><div className="tl-body"><span className="tl-label">Order cancelled</span></div></div>
      </div>
    );
  }
  const r = RANK[status];
  const steps = [
    { label: "Order placed", rank: 0 },
    { label: "Payment confirmed", rank: 1 },
    { label: "Shipped", rank: 2 },
    { label: "Delivered", rank: 3 },
  ];
  return (
    <div>
      {steps.map((s) => {
        const done = r >= s.rank;
        const now = r === s.rank;
        return (
          <div key={s.label} className={`tl-step${done ? " is-done" : ""}${now ? " is-now" : ""}`}>
            <span className="tl-dot">{done ? <Check /> : null}</span>
            <div className="tl-body"><span className="tl-label">{s.label}</span></div>
          </div>
        );
      })}
    </div>
  );
}

function OrderResult({ order }: { order: OrderResponse }) {
  const badge = BADGE[order.status];
  return (
    <>
      <div className="od-head">
        <div className="top">
          <span className="od-no">{order.orderNumber}</span>
          <span className={`st-badge ${badge.cls}`}>{badge.label}</span>
        </div>
      </div>

      <section className="od-section" aria-label="Order status">
        <h2>Status</h2>
        <Timeline status={order.status} />
        {order.trackingNumber ? (
          <div className="od-tracking">
            <div>
              <small>Carrier tracking number</small>
              <br />
              <span className="trk">{order.trackingNumber}</span>
            </div>
          </div>
        ) : (
          <p className="snapshot-note">{"// A carrier tracking number appears here once your order ships."}</p>
        )}
      </section>

      <section className="od-section" aria-label="Items">
        <h2>Items</h2>
        {order.items.map((item) => (
          <div className="od-item" key={item.id}>
            <div className="info">
              <span className="nm">{item.productName}</span>
              <span className="vr">Qty {item.quantity}</span>
              <span className="sk">{item.sku}</span>
            </div>
            <span className="pr">{fmt(item.lineTotal)}</span>
          </div>
        ))}
      </section>

      <section className="od-section" aria-label="Order total">
        <h2>Total</h2>
        <div className="summary-row total">
          <span>Total</span>
          <span className="amount">{fmt(order.total)}</span>
        </div>
      </section>
    </>
  );
}

function TrackInner() {
  const params = useSearchParams();
  const [number, setNumber] = useState(params.get("number") ?? "");
  const [email, setEmail] = useState("");
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setOrder(null);
    if (!number.trim() || !email.trim()) {
      setError("Enter both your order number and the email you used.");
      return;
    }
    setLoading(true);
    try {
      setOrder(await api.orders.track(number.trim(), email.trim()));
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 404
          ? "No order matches that number and email. Double-check both and try again."
          : "Couldn't look that up right now — please try again.",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="od-head">
        <h1 style={{ fontFamily: "var(--font-display)", textTransform: "uppercase", letterSpacing: ".02em" }}>
          Track your order
        </h1>
        <span className="od-date">Enter your order number and email — no account needed.</span>
      </div>

      <form className="auth-form" onSubmit={submit} style={{ marginBottom: 24 }}>
        <input className="text-input mono-input" placeholder="Order number (e.g. AD-…)" value={number} onChange={(e) => setNumber(e.target.value)} autoComplete="off" />
        <input className="text-input" type="email" placeholder="Email used at checkout" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
          {loading ? "Looking…" : "Track order"}
        </button>
      </form>

      {order && <OrderResult order={order} />}

      <p className="od-help">
        Have an account? <Link href="/orders">See all your orders</Link>
      </p>
    </>
  );
}

export default function TrackPage() {
  const inner = (
    <Suspense fallback={<p style={{ padding: 40, color: "var(--text-secondary)" }}>Loading…</p>}>
      <TrackInner />
    </Suspense>
  );
  return (
    <>
      <div className="lg:hidden">
        <div className="shell" data-quickadd="off">
          <TopBar />
          {inner}
        </div>
      </div>
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap" style={{ maxWidth: 720 }}>
          {inner}
        </div>
        <DesktopFooter />
      </div>
    </>
  );
}
