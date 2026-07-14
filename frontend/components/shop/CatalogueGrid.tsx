import { ProductCard, type ProductCardData } from "@/components/product/ProductCard";

/** 2-up (mobile) / multi-column (wide → dk-grid) product grid with an empty state. */
export function CatalogueGrid({
  products,
  wide = false,
}: {
  products: ProductCardData[];
  wide?: boolean;
}) {
  if (products.length === 0) {
    return (
      <p className="empty-note" role="status">
        No products here yet — check back soon.
      </p>
    );
  }
  return (
    <section
      className={`product-grid${wide ? " dk-grid" : ""}`}
      data-quickadd="off"
      aria-label="Catalogue"
    >
      {products.map((product) => (
        <ProductCard key={`${product.name}-${product.href}`} product={product} />
      ))}
    </section>
  );
}
