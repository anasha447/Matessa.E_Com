import { redirect } from "next/navigation";
import { Shell } from "@/components/layout/Shell";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { WishlistView } from "@/components/wishlist/WishlistView";
import { getAccessToken } from "@/lib/server/session";
import { getWishlist } from "@/lib/server/wishlist";

export const dynamic = "force-dynamic";

export default async function WishlistPage() {
  const token = await getAccessToken();
  if (!token) redirect("/login?next=/wishlist");

  const items = (await getWishlist()) ?? [];

  return (
    <>
      <div className="lg:hidden">
        <Shell>
          <WishlistView initial={items} />
        </Shell>
      </div>
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap">
          <WishlistView initial={items} />
        </div>
        <DesktopFooter />
      </div>
    </>
  );
}
