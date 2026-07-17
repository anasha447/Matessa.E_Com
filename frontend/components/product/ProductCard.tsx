import Link from "next/link";
import Image from "next/image";
import type { ProductCardData } from "@/lib/types/product-card";
import { formatMoney } from "@/lib/utils/money";
import { mediaUrl } from "@/lib/utils/media";
import { BrandBadge } from "@/components/ui/brand";

export type { ProductCardData };

/** Product card (DESIGN-SYSTEM.md §5): white card, 2rem image tile on light gray with
 *  mix-blend-multiply pack shots, thin gray border → orange on hover, shadow lift +
 *  slight scale, Asul name, ₹ price in green. */
export function ProductCard({ product }: { product: ProductCardData }) {
  const { name, category, price, image, alt, href, badge } = product;
  return (
    <Link
      href={href}
      className="group block rounded-[22px] border border-gray-300 bg-[#FFFFFF] p-3 shadow-lg transition-all duration-300 hover:-translate-y-1 hover:scale-[1.02] hover:border-orange hover:shadow-2xl"
    >
      <div className="relative overflow-hidden rounded-[2rem] bg-gray-100">
        {badge && (
          <div className="absolute left-3 top-3 z-10">
            <BrandBadge tone={badge.tone === "low" ? "orange" : "yellow"}>{badge.label}</BrandBadge>
          </div>
        )}
        <Image
          src={mediaUrl(image)}
          alt={alt}
          width={420}
          height={420}
          className="aspect-square w-full object-contain mix-blend-multiply transition-transform duration-500 group-hover:scale-110"
        />
      </div>
      <div className="px-2 pb-2 pt-3.5">
        <p className="font-body text-[11px] font-bold uppercase tracking-[0.18em] text-gray-400">{category}</p>
        <h3 className="mt-0.5 font-heading text-xl font-bold text-darkgreen">{name}</h3>
        <p className="mt-1 font-body text-lg font-bold text-green">{formatMoney(price)}</p>
      </div>
    </Link>
  );
}
