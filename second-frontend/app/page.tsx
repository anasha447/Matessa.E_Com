import { Shell } from "@/components/layout/Shell";
import { Marquee } from "@/components/layout/Marquee";
import { Hero } from "@/components/home/Hero";
import { HelmetDrop } from "@/components/home/HelmetDrop";
import { CategoryRow } from "@/components/home/CategoryRow";
import { ProductGrid } from "@/components/home/ProductGrid";
import { StoryBand } from "@/components/home/StoryBand";
import { AssuranceStrip } from "@/components/home/AssuranceStrip";
import { Reveal } from "@/components/Reveal";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { CategoryFilter } from "@/components/home/CategoryFilter";
import { DesktopStory } from "@/components/home/DesktopStory";
import { heroImagesWide, newsTicker } from "@/lib/stubs/home-desktop";
import { getFeaturedProducts, getStorefrontCategories } from "@/lib/server/catalog";

// Storefront data is fetched per request from the backend (falls back to design
// stubs when the API is unavailable), so render this route dynamically.
export const dynamic = "force-dynamic";

export default async function Home() {
  const [categories, products] = await Promise.all([
    getStorefrontCategories(),
    getFeaturedProducts(8),
  ]);

  return (
    <>
      {/* Mobile composition (< lg): 430px shell, bottom tab bar, helmet drop */}
      <div className="lg:hidden">
        <Shell>
          <Hero />
          <Marquee />
          <HelmetDrop />
          <CategoryRow categories={categories} />
          <ProductGrid products={products.slice(0, 4)} />
          <StoryBand />
          <AssuranceStrip />
        </Shell>
      </div>

      {/* Desktop composition (≥ lg): full-width nav, wide hero, filterable grid */}
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <Hero wide faceSrc={heroImagesWide.face} helmetSrc={heroImagesWide.helmet} />
        <Marquee items={newsTicker} label="Store news" />
        <CategoryFilter products={products} />
        <DesktopStory />
        <DesktopFooter />
      </div>

      <Reveal />
    </>
  );
}
