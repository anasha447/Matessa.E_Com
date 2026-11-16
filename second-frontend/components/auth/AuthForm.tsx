"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { safeNext } from "@/lib/utils/safeNext";
import { useCart } from "@/components/cart/CartContext";
import { ForcePasswordChange } from "./ForcePasswordChange";

type Mode = "login" | "register";

const isStaffRole = (roles: string[]) => roles.includes("ADMIN") || roles.includes("MANAGER");

export function AuthForm() {
  const router = useRouter();
  const params = useSearchParams();
  const { refresh: refreshCart } = useCart();
  // Explicit ?next wins (e.g. bounced from a protected page); otherwise we pick a
  // role-based default after login: staff → /admin, customer → /account.
  // Only a path on this site: ?next comes from a URL anyone can craft (open redirect).
  const nextParam = safeNext(params.get("next"));

  const [mode, setMode] = useState<Mode>("login");
  const [form, setForm] = useState({ email: "", password: "", firstName: "", lastName: "" });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  // Set when a provisioned manager logs in with a temp password — gates the redirect
  // behind a mandatory password change.
  const [forceChange, setForceChange] = useState<{ current: string; dest: string } | null>(null);

  const go = (dest: string) => {
    router.push(dest);
    router.refresh();
  };

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const url = mode === "login" ? "/api/session/login" : "/api/session/register";
      const payload =
        mode === "login"
          ? { email: form.email, password: form.password }
          : form;
      const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        setError(data.message || "Something went wrong. Please try again.");
        return;
      }
      await refreshCart();
      const roles: string[] = Array.isArray(data.roles) ? data.roles : [];
      const dest = nextParam || (isStaffRole(roles) ? "/admin" : "/account");
      // First-login temp password → force a change before landing on the dashboard.
      if (mode === "login" && data.mustChangePassword) {
        setForceChange({ current: form.password, dest });
        return;
      }
      go(dest);
    } catch {
      setError("Network error — please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
    <div className="auth-card">
      <Link href="/" aria-label="Adventure Dose home">
        <span className="auth-logo" role="img" aria-label="Adventure Dose logo" />
      </Link>
      <h1>{mode === "login" ? "Welcome back" : "Join the ride"}</h1>
      <p className="auth-sub">
        {mode === "login" ? "Sign in to your account" : "Create your Adventure Dose account"}
      </p>

      <div className="auth-tabs" role="tablist">
        <button
          type="button"
          className={`auth-tab${mode === "login" ? " is-active" : ""}`}
          onClick={() => setMode("login")}
          role="tab"
          aria-selected={mode === "login"}
        >
          Sign in
        </button>
        <button
          type="button"
          className={`auth-tab${mode === "register" ? " is-active" : ""}`}
          onClick={() => setMode("register")}
          role="tab"
          aria-selected={mode === "register"}
        >
          Create account
        </button>
      </div>

      <form className="auth-form" onSubmit={submit}>
        {mode === "register" && (
          <div className="auth-row">
            <input className="text-input" placeholder="First name" value={form.firstName} onChange={set("firstName")} required autoComplete="given-name" />
            <input className="text-input" placeholder="Last name" value={form.lastName} onChange={set("lastName")} required autoComplete="family-name" />
          </div>
        )}
        <input className="text-input" type="email" placeholder="Email" value={form.email} onChange={set("email")} required autoComplete="email" />
        <input className="text-input" type="password" placeholder="Password" value={form.password} onChange={set("password")} required minLength={8} autoComplete={mode === "login" ? "current-password" : "new-password"} />

        {error && <p className="auth-error" role="alert">{error}</p>}

        <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
          {loading ? "Please wait…" : mode === "login" ? "Sign in" : "Create account"}
        </button>
      </form>

      <p className="auth-alt">
        {mode === "login" ? "New to Adventure Dose? " : "Already have an account? "}
        <button
          type="button"
          className="auth-link"
          onClick={() => setMode(mode === "login" ? "register" : "login")}
        >
          {mode === "login" ? "Create an account" : "Sign in"}
        </button>
      </p>
    </div>
    {forceChange && (
      <ForcePasswordChange email={form.email} current={forceChange.current} onDone={() => go(forceChange.dest)} />
    )}
    </>
  );
}
