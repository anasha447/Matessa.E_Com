"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import "./NewsletterPopup.css";

const DISMISS_KEY = "ad_newsletter_dismissed";
const SHOW_DELAY_MS = 7000;
// Routes where the popup must never appear (admin console, auth, checkout funnel).
const HIDDEN_PREFIXES = ["/admin", "/login", "/checkout"];

/** Guest-only newsletter bubble: trades an email for a 15% welcome coupon. Appears once
 *  (localStorage-suppressed) after a short delay, and never inside the admin console or checkout. */
export function NewsletterPopup({ guest }: { guest: boolean }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [coupon, setCoupon] = useState<{ code: string; percent: number } | null>(null);
  const [copied, setCopied] = useState(false);

  const hidden = HIDDEN_PREFIXES.some((p) => pathname === p || pathname.startsWith(p + "/"));

  useEffect(() => {
    if (!guest || hidden || typeof window === "undefined") return;
    if (localStorage.getItem(DISMISS_KEY)) return;
    const t = setTimeout(() => setOpen(true), SHOW_DELAY_MS);
    return () => clearTimeout(t);
  }, [guest, hidden]);

  if (!guest || hidden || !open) return null;

  const suppress = () => {
    try {
      localStorage.setItem(DISMISS_KEY, "1");
    } catch {
      /* private mode — harmless, it just reappears next session */
    }
  };

  const dismiss = () => {
    suppress();
    setOpen(false);
  };

  const submit = async () => {
    setError(null);
    if (!/^\S+@\S+\.\S+$/.test(email)) {
      setError("Enter a valid email to get your code.");
      return;
    }
    setSubmitting(true);
    try {
      const res = await api.subscribers.join({ email: email.trim() });
      setCoupon({ code: res.couponCode, percent: res.discountPercent });
      suppress();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  const copyCode = async () => {
    if (!coupon) return;
    try {
      await navigator.clipboard.writeText(coupon.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard blocked — the code is visible to copy manually */
    }
  };

  return (
    <div className="nl-pop" role="dialog" aria-label="Newsletter signup">
      <button className="nl-close" type="button" aria-label="Close" onClick={dismiss}>
        ×
      </button>
      {coupon ? (
        <div className="nl-body">
          <h3 className="nl-title">You&apos;re in! 🎉</h3>
          <p className="nl-sub">Here&apos;s your {coupon.percent}% off code — use it at checkout.</p>
          <button className="nl-code" type="button" onClick={copyCode} title="Copy code">
            <span>{coupon.code}</span>
            <span className="nl-copy">{copied ? "Copied ✓" : "Copy"}</span>
          </button>
        </div>
      ) : (
        <div className="nl-body">
          <h3 className="nl-title">Get 15% off your first order</h3>
          <p className="nl-sub">Join the list — we&apos;ll send a code you can use right away.</p>
          <input
            className="text-input nl-input"
            type="email"
            placeholder="you@email.com"
            aria-label="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()}
            autoComplete="email"
          />
          {error && (
            <p className="nl-error" role="alert">
              {error}
            </p>
          )}
          <button
            className="btn btn-primary btn-block nl-submit"
            type="button"
            onClick={submit}
            disabled={submitting}
          >
            {submitting ? "Getting your code…" : "Get my 15% code"}
          </button>
          <button className="nl-dismiss" type="button" onClick={dismiss}>
            No thanks
          </button>
        </div>
      )}
    </div>
  );
}
