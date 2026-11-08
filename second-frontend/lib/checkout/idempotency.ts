/**
 * A checkout idempotency key that survives a page reload.
 *
 * The key exists so that a resubmitted checkout returns the original order instead of
 * placing a second one and taking a second payment. Holding it in a `useRef` defeated
 * that: the ref dies with the component, so the single case it most needed to cover —
 * "the payment went through but I never saw the confirmation, let me refresh and try
 * again" — minted a fresh key and produced a duplicate order and a duplicate charge.
 *
 * sessionStorage is the right lifetime here: it survives reload and in-tab navigation,
 * and is discarded when the tab closes, so a later unrelated checkout never reuses it.
 *
 * The key is bound to a fingerprint of what is being bought. Without that binding a
 * stored key would be replayed for a *different* cart, and the server — correctly
 * treating it as a repeat — would hand back the earlier order rather than buying what
 * the shopper now has in the basket. A changed cart is a different purchase, so it gets
 * a different key.
 *
 * Every access is wrapped: sessionStorage throws rather than returning null in some
 * privacy modes, and a storage failure must degrade to a fresh key, never break checkout.
 */

const STORAGE_KEY = "checkout:idempotency";

export interface FingerprintLine {
  productVariantId: number;
  quantity: number;
}

/** Stable identity for "this basket, with this coupon". Order-independent. */
export function cartFingerprint(lines: FingerprintLine[], couponCode: string | null): string {
  const normalised = lines
    .map((line) => `${line.productVariantId}x${line.quantity}`)
    .sort()
    .join("|");
  return `${normalised}#${couponCode ?? ""}`;
}

/** The key for this basket — the stored one when it still matches, otherwise a new one. */
export function checkoutIdempotencyKey(fingerprint: string): string {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (raw) {
      const saved = JSON.parse(raw) as { fingerprint?: string; key?: string };
      if (saved.key && saved.fingerprint === fingerprint) {
        return saved.key;
      }
    }
  } catch {
    /* storage unreadable — fall through and mint a fresh key */
  }

  const key = crypto.randomUUID();
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ fingerprint, key }));
  } catch {
    /* storage unwritable — the key still works for retries within this page */
  }
  return key;
}

/** Call once the order is placed, so the next checkout starts clean. */
export function clearCheckoutIdempotencyKey(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    /* nothing to do */
  }
}
