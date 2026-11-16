import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { cookies } from "next/headers";
import { TopBar } from "@/components/layout/TopBar";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { CheckoutForm } from "@/components/checkout/CheckoutForm";
import { GuestCheckout } from "@/components/checkout/GuestCheckout";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/session/cookies";
import { getCurrentUser } from "@/lib/server/session";
import { getCartView } from "@/lib/server/cart";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default async function CheckoutPage({
  searchParams,
}: {
  searchParams: Promise<{ coupon?: string }>;
}) {
  const { coupon } = await searchParams;
  const initialCoupon = coupon && coupon.trim() ? coupon.trim().slice(0, 40) : null;

  const jar = await cookies();
  const authed = jar.has(ACCESS_COOKIE) || jar.has(REFRESH_COOKIE);

  // Guest checkout: no account. The cart is client-side, so a client component reads and
  // re-prices it, then submits to the public guest-checkout endpoint.
  if (!authed) {
    return (
      <>
        <div className="lg:hidden">
          <div className="shell" data-quickadd="off">
            <TopBar />
            <GuestCheckout initialCoupon={initialCoupon} />
          </div>
        </div>
        <div className="dk hidden lg:block">
          <DesktopHeader />
          <div className="dk-wrap">
            <GuestCheckout initialCoupon={initialCoupon} />
          </div>
          <DesktopFooter />
        </div>
      </>
    );
  }

  const [cart, user] = await Promise.all([getCartView(), getCurrentUser()]);
  if (!cart || cart.items.length === 0) redirect("/cart");

  const userInfo = user
    ? { email: user.email, fullName: `${user.firstName} ${user.lastName}`.trim() }
    : null;

  return (
    <>
      {/* Mobile (< lg): bare shell + sticky place-order bar (no tab bar on checkout) */}
      <div className="lg:hidden">
        <div className="shell" data-quickadd="off">
          <TopBar />
          <CheckoutForm cart={cart} user={userInfo} initialCoupon={initialCoupon} />
        </div>
      </div>

      {/* Desktop (≥ lg): 2-column — form left, summary + place order right */}
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap">
          <CheckoutForm cart={cart} user={userInfo} initialCoupon={initialCoupon} />
        </div>
        <DesktopFooter />
      </div>
    </>
  );
}
