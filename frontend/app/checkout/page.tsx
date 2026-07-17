import type { Metadata } from "next";
import { cookies } from "next/headers";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { CheckoutView } from "@/components/checkout/CheckoutView";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/session/cookies";
import { getCurrentUser } from "@/lib/server/session";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Checkout — Matessa" };

export default async function CheckoutPage({
  searchParams,
}: {
  searchParams: Promise<{ coupon?: string }>;
}) {
  const { coupon } = await searchParams;
  const initialCoupon = coupon && coupon.trim() ? coupon.trim() : null;

  const jar = await cookies();
  const authed = jar.has(ACCESS_COOKIE) || jar.has(REFRESH_COOKIE);
  const user = authed ? await getCurrentUser() : null;
  const userInfo = user ? { email: user.email, fullName: `${user.firstName} ${user.lastName}`.trim() } : null;

  return (
    <>
      <SiteHeader />
      <main className="min-h-[70svh]">
        <CheckoutView user={userInfo} initialCoupon={initialCoupon} guest={!authed} />
      </main>
      <SiteFooter variant="minimal" />
    </>
  );
}
