import { notFound, redirect } from "next/navigation";
import Link from "next/link";
import { TopBar } from "@/components/layout/TopBar";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { CancelOrderButton } from "@/components/orders/CancelOrderButton";
import { getAccessToken, serverGet } from "@/lib/server/session";
import type { OrderResponse, OrderStatus } from "@/lib/api";

export const dynamic = "force-dynamic";

const fmt = (n: number) => `$${n.toFixed(2)}`;

const BADGE: Record<OrderStatus, { cls: string; label: string }> = {
  PENDING: { cls: "st-paid", label: "Pending" },
  PAID: { cls: "st-paid", label: "Paid" },
  SHIPPED: { cls: "st-shipped", label: "Shipped" },
  DELIVERED: { cls: "st-delivered", label: "Delivered" },
  CANCELLED: { cls: "st-cancelled", label: "Cancelled" },
};

const RANK: Record<OrderStatus, number> = {
  PENDING: 0,
  PAID: 1,
  SHIPPED: 2,
  DELIVERED: 3,
  CANCELLED: -1,
};

function variantLabel(sku: string | null | undefined): string | null {
  if (!sku) return null;
  const m = sku.match(/-(XS|S|M|L|XL|XXL)$/i);
  if (m) return `Size ${m[1].toUpperCase()}`;
  if (/-OS$/i.test(sku)) return "One size";
  return null;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? ""
    : d.toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" });
}

function parseAddress(json: string): string {
  try {
    const a = JSON.parse(json);
    return [
      a.recipientName,
      a.line1,
      a.line2,
      [a.city, a.state, a.postalCode].filter(Boolean).join(", "),
      a.country,
      a.phone,
    ]
      .filter(Boolean)
      .join("\n");
  } catch {
    return json;
  }
}

const Check = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 6 9 17l-5-5" />
  </svg>
);

function Timeline({ order }: { order: OrderResponse }) {
  let steps: { label: string; done: boolean; now?: boolean; fail?: boolean }[];
  if (order.status === "CANCELLED") {
    steps = [
      { label: "Order placed", done: true },
      { label: "Order cancelled", done: true, fail: true, now: true },
    ];
  } else {
    const r = RANK[order.status];
    steps = [
      { label: "Order placed", rank: 0 },
      { label: "Payment confirmed", rank: 1 },
      { label: "Shipped", rank: 2 },
      { label: "Delivered", rank: 3 },
    ].map((s) => ({ label: s.label, done: r >= s.rank, now: r === s.rank }));
  }
  return (
    <div>
      {steps.map((s, i) => (
        <div
          key={i}
          className={`tl-step${s.done ? " is-done" : ""}${s.now ? " is-now" : ""}${s.fail ? " is-fail" : ""}`}
        >
          <span className="tl-dot">{s.done && !s.fail ? <Check /> : null}</span>
          <div className="tl-body">
            <span className="tl-label">{s.label}</span>
          </div>
        </div>
      ))}
    </div>
  );
}

export default async function OrderDetailPage({ params }: { params: Promise<{ orderNumber: string }> }) {
  const { orderNumber } = await params;
  const token = await getAccessToken();
  if (!token) redirect(`/login?next=/orders/${orderNumber}`);

  const order = await serverGet<OrderResponse>(`/api/orders/by-number/${encodeURIComponent(orderNumber)}`);
  if (!order) notFound();

  const badge = BADGE[order.status];
  const cancellable = order.status === "PENDING" || order.status === "PAID";

  const content = (
    <>
      <div className="od-head">
        <div className="top">
          <span className="od-no">{order.orderNumber}</span>
          <span className={`st-badge ${badge.cls}`}>{badge.label}</span>
        </div>
        <span className="od-date">Placed {formatDate(order.createdAt)}</span>
      </div>

      <section className="od-section" aria-label="Order status">
        <h2>Status</h2>
        <Timeline order={order} />
        {order.trackingNumber && (
          <div className="od-tracking">
            <div>
              <small>Tracking number</small>
              <br />
              <span className="trk">{order.trackingNumber}</span>
            </div>
          </div>
        )}
      </section>

      <section className="od-section" aria-label="Items">
        <h2>Items</h2>
        {order.items.map((item) => (
          <div className="od-item" key={item.id}>
            <div className="info">
              <span className="nm">{item.productName}</span>
              <span className="vr">
                {[variantLabel(item.sku), `Qty ${item.quantity}`].filter(Boolean).join(" · ")}
              </span>
              <span className="sk">{item.sku}</span>
            </div>
            <span className="pr">{fmt(item.lineTotal)}</span>
          </div>
        ))}
        <p className="snapshot-note">{"// Prices & names frozen at purchase time"}</p>
      </section>

      <section className="od-section" aria-label="Payment">
        <h2>Payment</h2>
        <div className="kv">
          <span className="k">Method</span>
          <span className="v">{order.paymentMethod}</span>
        </div>
        <div className="kv">
          <span className="k">Status</span>
          <span className="v">{order.paymentStatus}</span>
        </div>
        {order.transactionId && (
          <div className="kv">
            <span className="k">Transaction</span>
            <span className="v mono">{order.transactionId}</span>
          </div>
        )}
        {order.couponCode && (
          <div className="kv">
            <span className="k">Coupon</span>
            <span className="v mono">{order.couponCode}</span>
          </div>
        )}
      </section>

      <section className="od-section" aria-label="Shipping">
        <h2>Shipping</h2>
        <div className="kv">
          <span className="k">Method</span>
          <span className="v">{order.shippingMethod}</span>
        </div>
        <p className="addr">{parseAddress(order.shippingAddress)}</p>
      </section>

      <section className="od-section" aria-label="Order total">
        <h2>Total</h2>
        <div className="summary-row">
          <span>Subtotal</span>
          <strong>{fmt(order.subtotal)}</strong>
        </div>
        {order.discountAmount > 0 && (
          <div className="summary-row">
            <span>Discount</span>
            <strong>-{fmt(order.discountAmount)}</strong>
          </div>
        )}
        <div className="summary-row">
          <span>Shipping</span>
          <strong>Free</strong>
        </div>
        <div className="summary-row total">
          <span>Total</span>
          <span className="amount">{fmt(order.total)}</span>
        </div>
      </section>

      {cancellable && (
        <div className="od-actions">
          <CancelOrderButton orderId={order.id} />
        </div>
      )}
      <p className="od-help">
        Need help with this order? <Link href="/contact">Contact us</Link>
      </p>
    </>
  );

  return (
    <>
      <div className="lg:hidden">
        <div className="shell" data-quickadd="off">
          <TopBar />
          {content}
        </div>
      </div>
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap" style={{ maxWidth: 720 }}>
          {content}
        </div>
        <DesktopFooter />
      </div>
    </>
  );
}
