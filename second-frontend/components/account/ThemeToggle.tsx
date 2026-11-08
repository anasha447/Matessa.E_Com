"use client";

import { useEffect, useState } from "react";

/** Dusk (dark) ↔ light toggle. Persists to localStorage; applies to <html data-theme>. */
export function ThemeToggle() {
  const [dark, setDark] = useState(true);

  useEffect(() => {
    const saved = typeof localStorage !== "undefined" ? localStorage.getItem("ad-theme") : null;
    const isDark = saved ? saved === "dark" : document.documentElement.dataset.theme !== "light";
    // Initialize from the persisted preference on mount.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setDark(isDark);
    document.documentElement.dataset.theme = isDark ? "dark" : "light";
  }, []);

  const toggle = () => {
    const next = !dark;
    setDark(next);
    document.documentElement.dataset.theme = next ? "dark" : "light";
    localStorage.setItem("ad-theme", next ? "dark" : "light");
  };

  return (
    <button
      className="switch"
      type="button"
      role="switch"
      aria-checked={dark}
      aria-label="Toggle dusk mode"
      onClick={toggle}
    >
      <span className="knob" />
    </button>
  );
}
