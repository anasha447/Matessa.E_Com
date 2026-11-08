import type { ProductDetail } from "@/lib/server/product";

/** Identity block: SKU + share · name · category · rating · price. */
export function ProductInfo({ product }: { product: ProductDetail }) {
  const { sku, name, categoryName, brand, price, rating, reviewCount } = product;
  return (
    <section className="pdp-info">
      <div className="pdp-meta">
        <span className="pdp-sku">{sku || brand}</span>
        <button className="pdp-share" type="button">
          <svg
            width="17"
            height="17"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="18" cy="5" r="3" />
            <circle cx="6" cy="12" r="3" />
            <circle cx="18" cy="19" r="3" />
            <path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4" />
          </svg>
          Share
        </button>
      </div>
      <h1>{name}</h1>
      <p className="pdp-cat">{categoryName}</p>
      <div className="pdp-rating">
        {reviewCount > 0 ? (
          <>
            <span className="star">★★★★★</span>
            <span>
              {rating != null ? `${rating.toFixed(1)} · ` : ""}
              {reviewCount} review{reviewCount === 1 ? "" : "s"}
            </span>
          </>
        ) : (
          <span>No reviews yet</span>
        )}
      </div>
      <div className="pdp-price-row">
        <span className="pdp-price">${price}</span>
        <span className="pdp-tax">Price incl. of all taxes</span>
      </div>
    </section>
  );
}
