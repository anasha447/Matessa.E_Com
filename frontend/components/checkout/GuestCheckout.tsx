"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { CheckoutForm } from "./CheckoutForm";
import type { CartViewData } from "@/lib/server/cart";

const GUEST_KEY = "ad_guest_cart";

/** Checkout for guests. The cart lives client-side, so we read it here, re-price it
 *  server-side (authoritative), and hand it to the shared CheckoutForm in guest mode.
 *  Empty guest cart → back to /cart. */
export function GuestCheckout({ initialCoupon }: { initialCoupon: string | null }) {
  const router = useRouter();
  const [cart, setCart] = useState<CartViewData | null>(null);
  const [empty, setEmpty] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      let lines: { productVariantId: number; quantity: number }[] = [];
      try {
        const raw = JSON.parse(localStorage.getItem(GUEST_KEY) || "[]");
        if (Array.isArray(raw)) lines = raw.filter((l) => l && typeof l.productVariantId === "number");
      } catch {
        /* ignore */
      }
      if (lines.length === 0) {
        if (!cancelled) setEmpty(true);
        return;
      }
      try {
        const priced = await api.cart.price(lines);
        const available = priced.items.filter((i) => i.available);
        if (available.length === 0) {
          if (!cancelled) setEmpty(true);
          return;
        }
        if (!cancelled) {
          setCart({
            cartId: null,
            userId: null,
            items: available.map((i) => ({ ...i, image: "" })),
            subtotal: priced.subtotal,
          });
        }
      } catch {
        if (!cancelled) setEmpty(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (empty) router.replace("/cart");
  }, [empty, router]);

  if (!cart) {
    return <p style={{ padding: 40, color: "var(--text-secondary)" }}>Loading your cart…</p>;
  }
  return <CheckoutForm cart={cart} user={null} initialCoupon={initialCoupon} guest />;
}
