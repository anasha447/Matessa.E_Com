"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { api } from "@/lib/api";
import type { CartResponse } from "@/lib/api";

export const CART_PLACEHOLDER = "/brand/leaf-pattern-light.webp";
const GUEST_KEY = "ad_guest_cart";

/** A guest cart line kept in localStorage. Priced on demand via /api/cart/price so
 *  totals stay server-authoritative (never computed client-side). */
type GuestLine = { productVariantId: number; quantity: number };

function readGuest(): GuestLine[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = JSON.parse(localStorage.getItem(GUEST_KEY) || "[]");
    return Array.isArray(raw)
      ? raw.filter((l) => l && typeof l.productVariantId === "number" && typeof l.quantity === "number")
      : [];
  } catch {
    return [];
  }
}
function writeGuest(lines: GuestLine[]) {
  try {
    localStorage.setItem(GUEST_KEY, JSON.stringify(lines));
  } catch {
    /* storage unavailable — cart is best-effort for guests */
  }
}

/** Empty the guest cart (e.g. after a guest order is placed). Call refresh() after. */
export function clearGuestCart() {
  writeGuest([]);
}
const EMPTY_GUEST_CART: CartResponse = { cartId: null, userId: null, items: [], subtotal: 0 };

/** Retry a transient cart call — the gateway circuit breaker briefly 503s cart ops when
 *  order-service GC-pauses; a short retry rides through it instead of erroring the user. */
async function withRetry<T>(fn: () => Promise<T>, attempts = 3): Promise<T> {
  let lastErr: unknown;
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn();
    } catch (e) {
      lastErr = e;
      if (i < attempts - 1) await new Promise((r) => setTimeout(r, 400 * (i + 1)));
    }
  }
  throw lastErr;
}

interface CartContextValue {
  cart: CartResponse | null;
  images: Record<number, string>;
  /** weight/size label per productVariantId, e.g. "250g" (enriched from the variant). */
  sizes: Record<number, string>;
  count: number;
  subtotal: number;
  isOpen: boolean;
  open: () => void;
  close: () => void;
  refresh: () => Promise<void>;
  /** Adds to the server cart (signed in) or the local guest cart. Does not open the drawer. */
  addItem: (productVariantId: number, quantity?: number) => Promise<void>;
  setQty: (itemId: number, quantity: number) => Promise<void>;
  removeItem: (itemId: number) => Promise<void>;
}

const CartCtx = createContext<CartContextValue | null>(null);

export function useCart(): CartContextValue {
  const ctx = useContext(CartCtx);
  if (!ctx) throw new Error("useCart must be used within <CartProvider>");
  return ctx;
}

/** `authed` is derived server-side from the session cookie (see the root layout). It flips
 *  to true after login via router.refresh(), which triggers the guest→account cart merge. */
export function CartProvider({ children, authed }: { children: ReactNode; authed: boolean }) {
  const [cart, setCart] = useState<CartResponse | null>(null);
  const [images, setImages] = useState<Record<number, string>>({});
  const [sizes, setSizes] = useState<Record<number, string>>({});
  const [isOpen, setIsOpen] = useState(false);
  const imgCache = useRef<Map<number, string>>(new Map());
  const sizeCache = useRef<Map<number, string>>(new Map());

  // Resolve a primary image + the weight/size label per cart line (backend cart lines
  // carry neither). Cached by productVariantId.
  const enrich = useCallback(async (c: CartResponse) => {
    const missing = c.items.filter((i) => !imgCache.current.has(i.productVariantId));
    await Promise.all(
      missing.map(async (i) => {
        try {
          const ps = await api.variants.getPriceStock(i.productVariantId);
          const [imgs, variants] = await Promise.all([
            api.images.listForProduct(ps.productId),
            // the price/stock endpoint has no size; the variant list carries the label
            api.variants.listForProduct(ps.productId).catch(() => []),
          ]);
          const size = variants.find((v) => v.id === i.productVariantId)?.size;
          if (size) sizeCache.current.set(i.productVariantId, size);
          const primary = imgs.find((x) => x.primary) ?? imgs[0];
          imgCache.current.set(i.productVariantId, primary?.url ?? CART_PLACEHOLDER);
        } catch {
          imgCache.current.set(i.productVariantId, CART_PLACEHOLDER);
        }
      }),
    );
    if (missing.length > 0) {
      setImages(Object.fromEntries(imgCache.current));
      setSizes(Object.fromEntries(sizeCache.current));
    }
  }, []);

  const apply = useCallback(
    async (c: CartResponse) => {
      setCart(c);
      await enrich(c);
    },
    [enrich],
  );

  // Guest cart: re-price the local lines (or show empty). Retries a few times so a
  // transient gateway hiccup (order-service GC pause → 503) doesn't drop the just-added
  // item, and on persistent failure keeps whatever is already shown rather than wiping it.
  const priceGuest = useCallback(async () => {
    const lines = readGuest();
    if (lines.length === 0) {
      setCart(EMPTY_GUEST_CART);
      return;
    }
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        await apply(await api.cart.price(lines));
        return;
      } catch {
        if (attempt < 2) await new Promise((r) => setTimeout(r, 500 * (attempt + 1)));
      }
    }
    setCart((prev) => (prev && prev.items.length > 0 ? prev : EMPTY_GUEST_CART));
  }, [apply]);

  const refresh = useCallback(async () => {
    if (authed) {
      try {
        await apply(await api.cart.get());
      } catch {
        setCart(null); // transient error → treat as empty
      }
    } else {
      await priceGuest();
    }
  }, [authed, apply, priceGuest]);

  // Mount + auth transitions. On sign-in, merge any guest cart into the server cart first.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (authed) {
        const guest = readGuest();
        if (guest.length > 0) {
          for (const l of guest) {
            try {
              await api.cart.addItem({ productVariantId: l.productVariantId, quantity: l.quantity });
            } catch {
              /* skip lines that can't be added (gone/out of stock) */
            }
          }
          writeGuest([]);
        }
        try {
          const c = await api.cart.get();
          if (!cancelled) await apply(c);
        } catch {
          if (!cancelled) setCart(null);
        }
      } else if (!cancelled) {
        await priceGuest();
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authed]);

  const addItem = useCallback(
    async (productVariantId: number, quantity = 1) => {
      if (authed) {
        await apply(await withRetry(() => api.cart.addItem({ productVariantId, quantity })));
        return;
      }
      const lines = readGuest();
      const existing = lines.find((l) => l.productVariantId === productVariantId);
      if (existing) existing.quantity += quantity;
      else lines.push({ productVariantId, quantity });
      writeGuest(lines);
      await priceGuest();
    },
    [authed, apply, priceGuest],
  );

  const setQty = useCallback(
    async (itemId: number, quantity: number) => {
      if (authed) {
        if (quantity <= 0) {
          await api.cart.removeItem(itemId);
          await refresh();
        } else {
          await apply(await api.cart.updateItem(itemId, { quantity }));
        }
        return;
      }
      // guest lines are keyed by productVariantId (see /api/cart/price)
      let lines = readGuest();
      if (quantity <= 0) lines = lines.filter((l) => l.productVariantId !== itemId);
      else {
        const l = lines.find((x) => x.productVariantId === itemId);
        if (l) l.quantity = quantity;
      }
      writeGuest(lines);
      await priceGuest();
    },
    [authed, apply, refresh, priceGuest],
  );

  const removeItem = useCallback(
    async (itemId: number) => {
      if (authed) {
        await api.cart.removeItem(itemId);
        await refresh();
        return;
      }
      writeGuest(readGuest().filter((l) => l.productVariantId !== itemId));
      await priceGuest();
    },
    [authed, refresh, priceGuest],
  );

  const count = cart?.items.reduce((s, i) => s + i.quantity, 0) ?? 0;
  const subtotal = cart?.subtotal ?? 0;

  return (
    <CartCtx.Provider
      value={{
        cart,
        images,
        sizes,
        count,
        subtotal,
        isOpen,
        open: () => setIsOpen(true),
        close: () => setIsOpen(false),
        refresh,
        addItem,
        setQty,
        removeItem,
      }}
    >
      {children}
    </CartCtx.Provider>
  );
}
