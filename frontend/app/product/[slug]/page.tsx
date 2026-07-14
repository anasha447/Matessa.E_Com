import { notFound } from "next/navigation";
import { TopBar } from "@/components/layout/TopBar";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { Reveal } from "@/components/Reveal";
import { ProductGallery } from "@/components/product/ProductGallery";
import { ProductInfo } from "@/components/product/ProductInfo";
import { ProductBuyPanel } from "@/components/product/ProductBuyPanel";
import { getProductDetailBySlug, type ProductDetail } from "@/lib/server/product";

export const dynamic = "force-dynamic";

const ChevronDown = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="m6 9 6 6 6-6" />
  </svg>
);

/** Shared, brand-generic detail sections (backend has no spec fields; these apply
 *  across products). Product description is shown when present. */
function DetailSections({ product }: { product: ProductDetail }) {
  return (
    <>
      {product.description && (
        <section className="pdp-section">
          <div className="pdp-section-head">
            <h2>The story</h2>
          </div>
          <p style={{ fontSize: "14.5px", color: "var(--text-secondary)", lineHeight: 1.6 }}>
            {product.description}
          </p>
        </section>
      )}

      <section className="pdp-section">
        <div className="return-note">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
            <path d="M3 3v5h5" />
          </svg>
          Eligible for return or exchange within 30 days of delivery. No questions asked.
        </div>
      </section>

      <details className="pdp-acc">
        <summary>
          Care &amp; maintenance
          <ChevronDown />
        </summary>
        <div className="acc-body">
          <strong>Shell:</strong> wipe with a damp microfiber cloth — no solvents on painted art.
          <br />
          <strong>Liner:</strong> remove and hand-wash cold, air dry.
          <br />
          <strong>Visor:</strong> rinse before wiping to avoid micro-scratches.
        </div>
      </details>
      <details className="pdp-acc">
        <summary>
          Shipping &amp; returns
          <ChevronDown />
        </summary>
        <div className="acc-body">
          Free shipping over $99 · dispatched in 1–2 business days · 30-day returns, no questions
          asked.
        </div>
      </details>
    </>
  );
}

export default async function ProductPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const product = await getProductDetailBySlug(slug);
  if (!product) notFound();

  return (
    <>
      {/* Mobile (< lg): full-width gallery, stacked info, fixed add-to-cart bar */}
      <div className="lg:hidden">
        <div className="shell" data-quickadd="off">
          <TopBar />
          <ProductGallery images={product.images} />
          <ProductInfo product={product} />
          <ProductBuyPanel variants={product.variants} />
          <DetailSections product={product} />
        </div>
      </div>

      {/* Desktop (≥ lg): 2-column — sticky gallery left, details right */}
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap">
          <div className="pdp-dk">
            <div className="pdp-dk-gallery">
              <ProductGallery images={product.images} />
            </div>
            <div className="pdp-dk-details">
              <ProductInfo product={product} />
              <ProductBuyPanel variants={product.variants} sticky={false} />
              <DetailSections product={product} />
            </div>
          </div>
        </div>
        <DesktopFooter />
      </div>

      <Reveal />
    </>
  );
}
