// Server-side catalog data for storefront pages. Runs in Server Components: it calls
// the public catalog endpoints through @/lib/api (no auth needed) and assembles the
// backend DTOs into the view models the design components consume. Every function
// degrades gracefully to the design's stub content if the backend is down or empty,
// so pages always render.
import { api } from "@/lib/api";
import type { CategoryResponse, ProductResponse } from "@/lib/api";
import type { ProductCardData } from "@/components/product/ProductCard";
import {
  categories as stubCategories,
  latestProducts as stubProducts,
  type CategoryTile,
} from "@/lib/stubs/home";

// Fallback image when a product has no images yet in the catalog.
const PLACEHOLDER_IMAGE = "/design/helmetVenomFront.png";

/** Top-level categories with a live product count, mapped to the nav tiles/tabs. */
export async function getStorefrontCategories(): Promise<CategoryTile[]> {
  try {
    const cats = await api.categories.list();
    if (cats.length === 0) return stubCategories;
    const tiles = await Promise.all(
      cats.map(async (c): Promise<CategoryTile> => {
        let count = 0;
        try {
          count = (await api.products.list({ categoryId: c.id, size: 1 })).page.totalElements;
        } catch {
          // count is best-effort; leave 0 if the products call fails
        }
        return { name: c.name, count, href: `/shop?category=${c.id}` };
      }),
    );
    return tiles;
  } catch {
    return stubCategories;
  }
}

/** Newest products assembled into product cards (name, price, primary image, category). */
export async function getFeaturedProducts(limit = 8): Promise<ProductCardData[]> {
  try {
    const [page, cats] = await Promise.all([
      api.products.list({ size: limit }),
      api.categories.list().catch(() => [] as CategoryResponse[]),
    ]);
    if (page.content.length === 0) return stubProducts;
    const nameById = new Map(cats.map((c) => [c.id, c.name]));
    return await Promise.all(
      page.content.map((p) => toProductCard(p, nameById.get(p.categoryId))),
    );
  } catch {
    return stubProducts;
  }
}

export interface CategoryNavItem {
  id: number;
  name: string;
}

/** Lightweight category nav (id + name) for the shop tabs. */
export async function getCategoryNav(): Promise<CategoryNavItem[]> {
  try {
    const cats = await api.categories.list();
    return cats.map((c) => ({ id: c.id, name: c.name }));
  } catch {
    return stubCategories.map((c, i) => ({ id: i + 1, name: c.name }));
  }
}

/** A page of products as cards, optionally filtered by category. Empty category →
 *  empty grid (legit); only a backend error falls back to stubs. */
export async function getProductsPage(
  opts: { categoryId?: number; page?: number; size?: number } = {},
): Promise<{ items: ProductCardData[]; total: number }> {
  try {
    const [page, cats] = await Promise.all([
      api.products.list({ categoryId: opts.categoryId, page: opts.page ?? 0, size: opts.size ?? 24 }),
      api.categories.list().catch(() => [] as CategoryResponse[]),
    ]);
    const nameById = new Map(cats.map((c) => [c.id, c.name]));
    const items = await Promise.all(
      page.content.map((p) => toProductCard(p, nameById.get(p.categoryId))),
    );
    return { items, total: page.page.totalElements };
  } catch {
    return { items: stubProducts, total: stubProducts.length };
  }
}

async function toProductCard(
  product: ProductResponse,
  categoryName?: string,
): Promise<ProductCardData> {
  let image = PLACEHOLDER_IMAGE;
  let alt = product.name;
  try {
    const images = await api.images.listForProduct(product.id);
    const primary = images.find((i) => i.primary) ?? images[0];
    if (primary) {
      image = primary.url;
      alt = primary.altText || product.name;
    }
  } catch {
    // no images endpoint / none set — keep the placeholder
  }
  return {
    name: product.name,
    category: categoryName ?? "Gear",
    price: product.basePrice,
    image,
    alt,
    href: `/product/${product.slug}`,
  };
}
