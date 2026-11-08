"use client";

import { useState } from "react";
import Link from "next/link";
import type { OrderStatus, OrderSummaryResponse } from "@/lib/api";

const fmt = (n: number) => `$${n.toFixed(2)}`;
const pad = (n: number) => `${n < 10 ? "0" : ""}${n}`;

const BADGE: Record<OrderStatus, { cls: string; label: string }> = {
  PENDING: { cls: "st-paid", label: "Pending" },
  PAID: { cls: "st-paid", label: "Paid" },
  SHIPPED: { cls: "st-shipped", label: "Shipped" },
  DELIVERED: { cls: "st-delivered", label: "Delivered" },
  CANCELLED: { cls: "st-cancelled", label: "Cancelled" },
};

function formatDate(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? ""
    : d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

const FILTERS = [
  { key: "all", label: "All" },
  { key: "active", label: "Active" },
  { key: "DELIVERED", label: "Delivered" },
  { key: "CANCELLED", label: "Cancelled" },
] as const;

type FilterKey = (typeof FILTERS)[number]["key"];

const ACTIVE: OrderStatus[] = ["PENDING", "PAID", "SHIPPED"];

export function OrdersList({ orders }: { orders: OrderSummaryResponse[] }) {
  const [filter, setFilter] = useState<FilterKey>("all");

  const visible = orders.filter((o) => {
    if (filter === "all") return true;
    if (filter === "active") return ACTIVE.includes(o.status);
    return o.status === filter;
  });

  return (
    <>
      <div className="orders-title">
        <h1>My orders</h1>
        <span className="meta">
          {"// "}
          {pad(visible.length)} order{visible.length === 1 ? "" : "s"}
        </span>
      </div>

      <div className="filter-chips" role="group" aria-label="Filter orders by status">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            className={`f-chip${filter === f.key ? " is-active" : ""}`}
            aria-pressed={filter === f.key}
            onClick={() => setFilter(f.key)}
          >
            {f.label}
          </button>
        ))}
      </div>

      <section aria-label="Orders">
        {visible.length === 0 ? (
          <p className="empty-note">
            {orders.length === 0 ? "No orders yet." : "No orders match this filter."}
          </p>
        ) : (
          visible.map((o) => {
            const badge = BADGE[o.status];
            return (
              <Link className="order-card" href={`/orders/${o.orderNumber}`} key={o.id}>
                <div className="oc-head">
                  <span className="oc-no">{o.orderNumber}</span>
                  <span className={`st-badge ${badge.cls}`}>{badge.label}</span>
                </div>
                <div className="oc-body">
                  <div className="oc-info">
                    <span className="oc-title">Placed {formatDate(o.createdAt)}</span>
                    <span className="oc-sub">
                      Payment {o.paymentStatus.toLowerCase()} · Shipping {o.shippingStatus.toLowerCase()}
                    </span>
                    <span className="oc-total">{fmt(o.total)}</span>
                  </div>
                  <svg className="oc-chev" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <path d="m9 6 6 6-6 6" />
                  </svg>
                </div>
              </Link>
            );
          })
        )}
      </section>

      {orders.length > 0 && <p className="orders-foot">{"// End of order history"}</p>}
    </>
  );
}
