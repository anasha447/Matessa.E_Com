"use client";

import { CartIcon } from "@/components/ui/icons";
import { useCart } from "./CartContext";

/** Header cart control: opens the drawer and shows the live item count (no badge when
 *  the cart is empty). */
export function CartButton() {
  const { count, open } = useCart();
  return (
    <button
      className="topbar-action cart-action"
      type="button"
      aria-label={`Cart, ${count} item${count === 1 ? "" : "s"}`}
      onClick={open}
    >
      <CartIcon />
      {count > 0 && <span className="mini-count">{count}</span>}
    </button>
  );
}
