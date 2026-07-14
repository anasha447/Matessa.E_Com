/* eslint-disable @next/next/no-img-element */
// Raw <img> (not next/image) keeps the design's CSS (.pc-media img object-fit/filter)
// working verbatim against pre-optimized static assets.
import Link from "next/link";
import { PlusIcon } from "@/components/ui/icons";

export interface ProductCardData {
  name: string;
  /** display label, e.g. "Helmets" / "Wearable Art" */
  category: string;
  price: number;
  image: string;
  alt: string;
  href: string;
  badge?: { label: string; tone?: "low" | "one" };
  /** optional CSS object-position for off-center crops */
  objectPosition?: string;
}

export function ProductCard({ product }: { product: ProductCardData }) {
  const { name, category, price, image, alt, href, badge, objectPosition } = product;
  return (
    <article className="product-card reveal">
      <div className="pc-media">
        {badge && (
          <span className={`badge${badge.tone ? ` is-${badge.tone}` : ""}`}>{badge.label}</span>
        )}
        <Link href={href} aria-label={`View ${name}`}>
          <img src={image} alt={alt} style={objectPosition ? { objectPosition } : undefined} />
        </Link>
      </div>
      <div className="pc-body">
        <span className="pc-cat">{category}</span>
        <h3 className="pc-name">{name}</h3>
        <div className="pc-row">
          <span className="pc-price">${price}</span>
          <button className="quick-add" type="button" aria-label={`Add ${name} to cart`}>
            <PlusIcon />
          </button>
        </div>
      </div>
    </article>
  );
}
