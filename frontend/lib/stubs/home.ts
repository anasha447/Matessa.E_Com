// Presentational stub data for the Home (Mobile) screen. UI-only phase: these mirror
// the design's sample content. They map onto backend DTOs later — e.g. HomeProduct ←
// ProductResponse + its primary image + cheapest ProductVariantResponse.effectivePrice,
// DropItem ← a "featured" ProductResponse. No live calls yet (see lib/api for the client).

export interface DropItem {
  sku: string;
  name: string;
  tagline: string;
  price: number;
  image: string;
  alt: string;
  /** drives the panel's color glow */
  glow: "orange" | "cool" | "steel";
  href: string;
}

export interface CategoryTile {
  name: string;
  count: number;
  href: string;
}

export interface HomeProduct {
  name: string;
  category: string;
  price: number;
  image: string;
  alt: string;
  href: string;
  badge?: { label: string; tone?: "low" | "one" };
}

export const dropItems: DropItem[] = [
  {
    sku: "HLM-VEN-ATL",
    name: "Venom Atlas",
    tagline: "Fanged carbon full-face. Track-bred, street-legal.",
    price: 329,
    image: "/design/helmetVenomFront.png",
    alt: "Venom Atlas full-face helmet, front view",
    glow: "orange",
    href: "#",
  },
  {
    sku: "HLM-VEN-SNW",
    name: "Venom Snow",
    tagline: "3-season liner, amber optics. Built for the cold.",
    price: 259,
    image: "/design/helmetVenomSnow.png",
    alt: "Venom Snow helmet with amber goggles in snowfall",
    glow: "cool",
    href: "#",
  },
  {
    sku: "HLM-GRF-001",
    name: "Griffin",
    tagline: "Hand-painted griffin over a matte ECE shell.",
    price: 289,
    image: "/design/helmetGriffin.png",
    alt: "Griffin hand-painted full-face helmet",
    glow: "steel",
    href: "#",
  },
];

export const categories: CategoryTile[] = [
  { name: "Helmets", count: 12, href: "#" },
  { name: "Clothes", count: 28, href: "#" },
  { name: "Artwork", count: 9, href: "#" },
];

export const latestProducts: HomeProduct[] = [
  {
    name: "Venom Atlas",
    category: "Helmets",
    price: 329,
    image: "/design/helmetVenomFront.png",
    alt: "Venom Atlas helmet",
    href: "#",
    badge: { label: "New" },
  },
  {
    name: "Griffin",
    category: "Helmets",
    price: 289,
    image: "/design/helmetGriffin.png",
    alt: "Griffin hand-painted helmet",
    href: "#",
    badge: { label: "3 left", tone: "low" },
  },
  {
    name: "Venom Snow",
    category: "Helmets",
    price: 259,
    image: "/design/helmetVenomSnow.png",
    alt: "Venom Snow helmet",
    href: "#",
  },
  {
    name: "Winged Classic",
    category: "Artwork",
    price: 310,
    image: "/design/helmetWingedClassic.png",
    alt: "Winged Classic painted helmet",
    href: "#",
    badge: { label: "1 of 1", tone: "one" },
  },
];

export const heroImages = {
  face: "/design/asset-9ba08359.jpg",
  helmet: "/design/asset-6ff33adf.jpg",
};

export const perks = [
  "Free shipping over $99",
  "30-day returns",
  "DOT & ECE certified",
  "Every piece has a story",
];
