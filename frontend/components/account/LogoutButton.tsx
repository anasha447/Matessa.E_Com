"use client";

import { useRouter } from "next/navigation";
import { useCart } from "@/components/cart/CartContext";

/** Clears the session cookie + cart badge, then returns home. */
export function LogoutButton() {
  const router = useRouter();
  const { refresh } = useCart();

  const logout = async () => {
    await fetch("/api/session/logout", { method: "POST" }).catch(() => {});
    await refresh();
    router.push("/");
    router.refresh();
  };

  return (
    <button className="btn btn-outline btn-block" type="button" onClick={logout}>
      Log out
    </button>
  );
}
