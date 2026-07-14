import { Shell } from "@/components/layout/Shell";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { Reveal } from "@/components/Reveal";
import { ShopTabs } from "@/components/shop/ShopTabs";
import { FilterBar } from "@/components/shop/FilterBar";
import { CatalogueGrid } from "@/components/shop/CatalogueGrid";
import { getCategoryNav, getProductsPage } from "@/lib/server/catalog";

export const dynamic = "force-dynamic";

const pad = (n: number) => `${n < 10 ? "0" : ""}${n}`;

export default async function ShopPage({
  searchParams,
}: {
  searchParams: Promise<{ category?: string }>;
}) {
  const { category } = await searchParams;
  const categoryId = category && /^\d+$/.test(category) ? Number(category) : undefined;

  const [categories, { items, total }] = await Promise.all([
    getCategoryNav(),
    getProductsPage({ categoryId, size: 24 }),
  ]);
  const activeName = categoryId ? categories.find((c) => c.id === categoryId)?.name : undefined;

  return (
    <>
      {/* Mobile (< lg) */}
      <div className="lg:hidden">
        <Shell>
          <ShopTabs categories={categories} activeId={categoryId} />
          <FilterBar count={total} />
          <CatalogueGrid products={items} />
        </Shell>
      </div>

      {/* Desktop (≥ lg) */}
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap">
          <div className="dk-section-head reveal">
            <h2>{activeName ?? "All products"}</h2>
            <span className="count">
              {pad(total)} item{total === 1 ? "" : "s"}
            </span>
          </div>
          <ShopTabs categories={categories} activeId={categoryId} wide />
          <CatalogueGrid products={items} wide />
        </div>
        <DesktopFooter />
      </div>

      <Reveal />
    </>
  );
}
