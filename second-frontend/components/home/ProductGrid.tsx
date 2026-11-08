import { SectionHeading } from "./SectionHeading";
import { ProductCard, type ProductCardData } from "@/components/product/ProductCard";
import { latestProducts } from "@/lib/stubs/home";

export function ProductGrid({ products = latestProducts }: { products?: ProductCardData[] }) {
  return (
    <>
      <SectionHeading
        title="Latest drops"
        id="latest-drops"
        link={{ label: "View all", href: "/shop" }}
      />
      <section className="product-grid" aria-label="Latest drops">
        {products.map((product) => (
          <ProductCard key={`${product.name}-${product.href}`} product={product} />
        ))}
      </section>
    </>
  );
}
