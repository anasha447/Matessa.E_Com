import { redirect } from "next/navigation";
import { Shell } from "@/components/layout/Shell";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { OrdersList } from "@/components/orders/OrdersList";
import { getAccessToken, serverGet } from "@/lib/server/session";
import type { OrderSummaryResponse, Page } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function OrdersPage() {
  const token = await getAccessToken();
  if (!token) redirect("/login?next=/orders");

  const page = await serverGet<Page<OrderSummaryResponse>>("/api/orders?page=0&size=50&sort=createdAt,desc");
  const orders = page?.content ?? [];

  return (
    <>
      <div className="lg:hidden">
        <Shell footer={false}>
          <OrdersList orders={orders} />
        </Shell>
      </div>
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap" style={{ maxWidth: 760 }}>
          <OrdersList orders={orders} />
        </div>
        <DesktopFooter />
      </div>
    </>
  );
}
