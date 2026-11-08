"use client";

import { useState } from "react";
import { useCart } from "@/components/cart/CartContext";

type Status = "idle" | "loading" | "nosize" | "error";

/** Adds the selected variant to the cart (works for guests — no sign-in required; the
 *  guest cart lives client-side and merges into the account on login). On success the
 *  button becomes a blue "View cart" (opens the drawer) — it does not auto-open. Reverts
 *  to "Add to cart" when a different variant is selected. */
export function AddToCart({ variantId, disabled }: { variantId: number | null; disabled?: boolean }) {
  const [status, setStatus] = useState<Status>("idle");
  const [addedVariant, setAddedVariant] = useState<number | null>(null);
  const { addItem, open } = useCart();

  const inCart = addedVariant !== null && addedVariant === variantId;

  const onClick = async () => {
    if (inCart) {
      open();
      return;
    }
    if (variantId == null) {
      setStatus("nosize");
      return;
    }
    setStatus("loading");
    try {
      await addItem(variantId, 1);
      setAddedVariant(variantId);
      setStatus("idle");
    } catch {
      setStatus("error");
    }
  };

  const label = inCart
    ? "View cart"
    : status === "loading"
      ? "Adding…"
      : status === "nosize"
        ? "Select a size"
        : status === "error"
          ? "Try again"
          : "Add to cart";

  return (
    <button
      className={`btn ${inCart ? "btn-incart" : "btn-primary"} btn-block`}
      type="button"
      onClick={onClick}
      disabled={disabled || status === "loading"}
    >
      {label}
    </button>
  );
}
