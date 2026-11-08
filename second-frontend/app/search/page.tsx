import { Shell } from "@/components/layout/Shell";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { SearchView } from "@/components/search/SearchView";
import { getProductsPage } from "@/lib/server/catalog";

export const dynamic = "force-dynamic";

export default async function SearchPage() {
  const { items } = await getProductsPage({ size: 50 });

  return (
    <>
      <div className="lg:hidden">
        <Shell footer={false}>
          <SearchView products={items} />
        </Shell>
      </div>
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap">
          <SearchView products={items} />
        </div>
        <DesktopFooter />
      </div>
    </>
  );
}
