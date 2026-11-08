"use client";

import { useState } from "react";
import { api, ApiError } from "@/lib/api";

/** Mandatory first-login password change. Shown after a provisioned manager signs in
 *  with their temporary password (mustChangePassword). Not dismissable — the only way
 *  out is to set a new password, after which onDone() sends them to their dashboard.
 *  `current` is the temp password they just authenticated with. */
export function ForcePasswordChange({
  current,
  onDone,
}: {
  current: string;
  onDone: () => void;
}) {
  const [pw, setPw] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (pw.length < 8) return setError("Use at least 8 characters.");
    if (pw !== confirm) return setError("The two passwords don't match.");
    if (pw === current) return setError("Choose a password different from the temporary one.");
    setBusy(true);
    try {
      await api.users.changePassword({ currentPassword: current, newPassword: pw });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't update your password — try again.");
      setBusy(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Set a new password"
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 100,
        display: "grid",
        placeItems: "center",
        background: "rgba(0,0,0,.62)",
        padding: 20,
      }}
    >
      <div className="auth-card">
        <h1>Set a new password</h1>
        <p className="auth-sub">
          Your account was created with a temporary password. Choose a new one to continue.
        </p>
        <form className="auth-form" onSubmit={submit}>
          <input
            className="text-input"
            type="password"
            placeholder="New password"
            value={pw}
            onChange={(e) => setPw(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
            autoFocus
          />
          <input
            className="text-input"
            type="password"
            placeholder="Confirm new password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
          />
          {error && <p className="auth-error" role="alert">{error}</p>}
          <button className="btn btn-primary btn-block" type="submit" disabled={busy}>
            {busy ? "Saving…" : "Save & continue"}
          </button>
        </form>
      </div>
    </div>
  );
}
