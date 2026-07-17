"use client";

import { useRouter } from "next/navigation";
import { useCart } from "@/components/cart/CartContext";

export function LogoutButton() {
  const router = useRouter();
  const { refresh } = useCart();
  return (
    <button
      type="button"
      onClick={async () => {
        await fetch("/api/session/logout", { method: "POST" });
        await refresh();
        router.push("/");
        router.refresh();
      }}
      className="rounded-full border-2 border-darkgreen px-6 py-2.5 font-body font-bold text-darkgreen transition-colors hover:border-orange hover:text-orange"
    >
      Sign out
    </button>
  );
}
