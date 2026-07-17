import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cookies } from "next/headers";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { ProductGallery } from "@/components/product/ProductGallery";
import { ProductBuyPanel } from "@/components/product/ProductBuyPanel";
import { CultureBand } from "@/components/product/CultureBand";
import { FaqAccordion } from "@/components/product/FaqAccordion";
import { ReviewsSection, Stars } from "@/components/product/ReviewsSection";
import { getProductDetailBySlug } from "@/lib/server/product";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/session/cookies";

export const dynamic = "force-dynamic";

// Per-slug ingredient note under the title (design's "Cinnamon · Cardamom · Clove" line).
const INGREDIENT_NOTE: Record<string, string> = {
  "masala-mate": "Cinnamon · Cardamom · Clove",
  "lemon-ginger-mate": "Lemon Peel · Ginger Root",
  "raw-mate": "Pure Loose Leaf",
};

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const product = await getProductDetailBySlug(slug);
  return { title: product ? `${product.name} — Matessa` : "Product — Matessa" };
}

export default async function ProductPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const product = await getProductDetailBySlug(slug);
  if (!product) notFound();

  const jar = await cookies();
  const authed = jar.has(ACCESS_COOKIE) || jar.has(REFRESH_COOKIE);
  const note = INGREDIENT_NOTE[product.slug];
  const isBlend = product.slug in INGREDIENT_NOTE;

  return (
    <>
      <SiteHeader />
      <main>
        <div className="mx-auto max-w-[1280px] px-5 py-8 nav:px-8 nav:py-12">
          {/* Breadcrumb */}
          <nav aria-label="Breadcrumb" className="mb-6 font-body text-sm text-gray-500">
            <Link href="/shop" className="hover:text-orange">
              Shop
            </Link>{" "}
            / <span className="text-darkgreen">{product.name}</span>
          </nav>

          <div className="grid gap-10 nav:grid-cols-2 nav:gap-14">
            <ProductGallery images={product.images} name={product.name} />

            <div>
              {note && (
                <p className="font-body text-[11px] font-bold uppercase tracking-[0.22em] text-lightgreen">{note}</p>
              )}
              <h1 className="mt-1 font-heading text-4xl font-bold text-darkgreen">{product.name}</h1>

              {/* rating */}
              <div className="mt-2 flex items-center gap-2">
                <Stars value={product.rating ?? 0} />
                <span className="font-body text-sm text-gray-500">
                  {product.rating != null
                    ? `${product.rating.toFixed(1)} · ${product.reviewCount} review${product.reviewCount === 1 ? "" : "s"}`
                    : "No reviews yet"}
                </span>
              </div>

              {product.description && (
                <p className="mt-4 font-body leading-relaxed text-gray-600">{product.description}</p>
              )}

              <ProductBuyPanel slug={product.slug} variants={product.variants} basePrice={product.price} />
            </div>
          </div>
        </div>

        {isBlend && <CultureBand />}
        <ReviewsSection productId={product.id} authed={authed} />
        <FaqAccordion />
      </main>
      <SiteFooter variant="full" />
    </>
  );
}
