import type { OrderStatus } from "@/lib/api";

// Order status pill — consistent color semantics with the admin dashboard.
const STYLES: Record<OrderStatus, string> = {
  PENDING: "bg-orange/15 text-orange",
  PAID: "bg-yellow/25 text-[#8a6d12]",
  SHIPPED: "bg-lightgreen/20 text-green",
  DELIVERED: "bg-green/15 text-green",
  CANCELLED: "bg-red-100 text-red-600",
};

const LABEL: Record<OrderStatus, string> = {
  PENDING: "Pending",
  PAID: "Paid",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return (
    <span className={`inline-flex items-center rounded-full px-3 py-1 font-body text-xs font-bold ${STYLES[status]}`}>
      {LABEL[status]}
    </span>
  );
}
