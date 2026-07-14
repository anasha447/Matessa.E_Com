import { Shell } from "@/components/layout/Shell";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { CartView } from "@/components/cart/CartView";
import { getCartView, type CartViewData } from "@/lib/server/cart";

export const dynamic = "force-dynamic";

export default async function CartPage() {
  // No auth gate: guests have a client-side cart (CartView reads it from the context).
  // getCartView returns the server cart when signed in, else an empty seed for first paint.
  const cart: CartViewData = (await getCartView()) ?? { cartId: 0, userId: 0, items: [], subtotal: 0 };

  return (
    <>
      {/* Mobile (< lg) */}
      <div className="lg:hidden">
        <Shell footer={false}>
          <CartView initial={cart} />
        </Shell>
      </div>

      {/* Desktop (≥ lg) */}
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap">
          <CartView initial={cart} />
        </div>
        <DesktopFooter />
      </div>
    </>
  );
}
