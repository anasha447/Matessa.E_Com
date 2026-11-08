"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";

/** Cancels a PENDING/PAID order. Backend returns 409 if it already shipped. */
export function CancelOrderButton({ orderId }: { orderId: number }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const cancel = async () => {
    if (!window.confirm("Cancel this order? This can't be undone.")) return;
    setBusy(true);
    setError(null);
    try {
      await api.orders.cancel(orderId);
      router.refresh();
    } catch (e) {
      setError(
        e instanceof ApiError && e.isConflict
          ? "This order can no longer be cancelled (it may have shipped)."
          : "Couldn't cancel the order — please try again.",
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <button className="btn btn-outline btn-block" type="button" onClick={cancel} disabled={busy}>
        {busy ? "Cancelling…" : "Cancel order"}
      </button>
      {error && <p className="co-error">{error}</p>}
    </>
  );
}
