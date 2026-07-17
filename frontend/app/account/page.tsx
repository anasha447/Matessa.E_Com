import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { LogoutButton } from "@/components/account/LogoutButton";
import { getCurrentUser } from "@/lib/server/session";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "My Account — Matessa" };

export default async function AccountPage() {
  const user = await getCurrentUser();
  if (!user) redirect("/login?next=/account");

  const name = `${user.firstName ?? ""} ${user.lastName ?? ""}`.trim() || user.email;

  return (
    <>
      <SiteHeader />
      <main className="min-h-[70svh]">
        <div className="mx-auto max-w-2xl px-5 py-12 nav:px-8">
          <h1 className="mb-8 font-heading text-3xl font-bold text-darkgreen md:text-4xl">My Account</h1>

          <div className="rounded-[22px] bg-white p-6 shadow-lg">
            <p className="font-heading text-lg font-bold text-darkgreen">{name}</p>
            <p className="font-body text-sm text-gray-500">{user.email}</p>
            <div className="mt-6 flex flex-wrap gap-3">
              <Link
                href="/orders"
                className="rounded-full bg-green px-6 py-2.5 font-body font-bold text-white transition-colors hover:bg-orange"
              >
                My orders
              </Link>
              <Link
                href="/wishlist"
                className="rounded-full border-2 border-gray-300 px-6 py-2.5 font-body font-bold text-darkgreen transition-colors hover:border-orange hover:text-orange"
              >
                Wishlist
              </Link>
              <LogoutButton />
            </div>
          </div>
        </div>
      </main>
      <SiteFooter variant="minimal" />
    </>
  );
}
