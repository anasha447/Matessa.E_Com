// Presentational stubs for the desktop Home composition. Maps onto backend DTOs later
// (catKey ← category filter; products ← ProductResponse). UI-only phase.
import type { ProductCardData } from "@/components/product/ProductCard";

export const heroImagesWide = {
  face: "/design/hero-face-wide.jpg",
  helmet: "/design/hero-helmet-wide.jpg",
};

export const newsTicker = [
  "New drop — Venom Atlas now shipping",
  "Free shipping over $99",
  "Winged Classic — 1 of 1, back in stock",
  "30-day returns",
  "DOT & ECE certified shells",
  "Every piece has a story",
];

export const desktopNav = [
  { label: "Helmets", href: "/shop" },
  { label: "Apparel", href: "/shop" },
  { label: "Wearable Art", href: "/shop" },
  { label: "Story", href: "/#story" },
];

export type CategoryKey = "All" | "Helmets" | "Clothes" | "Artwork";

export const categoryTabs: { label: string; key: CategoryKey }[] = [
  { label: "All", key: "All" },
  { label: "Helmets", key: "Helmets" },
  { label: "Apparel", key: "Clothes" },
  { label: "Wearable Art", key: "Artwork" },
];

export interface DesktopProduct extends ProductCardData {
  catKey: Exclude<CategoryKey, "All">;
}

export const collection: DesktopProduct[] = [
  {
    name: "Venom Atlas", category: "Helmets", catKey: "Helmets", price: 329,
    image: "/design/helmet-venom-front.png", alt: "Venom Atlas helmet, front view",
    href: "#", badge: { label: "New" },
  },
  {
    name: "Atlas Track", category: "Helmets", catKey: "Helmets", price: 349,
    image: "/design/helmet-venom-atlas.png", alt: "Atlas Track helmet, side profile", href: "#",
  },
  {
    name: "Griffin", category: "Helmets", catKey: "Helmets", price: 289,
    image: "/design/helmet-griffin.png", alt: "Griffin hand-painted helmet",
    href: "#", badge: { label: "3 left", tone: "low" },
  },
  {
    name: "Venom Snow", category: "Helmets", catKey: "Helmets", price: 259,
    image: "/design/helmet-venom-snow.png", alt: "Venom Snow helmet", href: "#",
  },
  {
    name: "Dusk Rider Jacket", category: "Apparel", catKey: "Clothes", price: 189,
    image: "/design/rider-dusk.png", alt: "Dusk Rider leather jacket worn on the road",
    href: "#", objectPosition: "50% 38%",
  },
  {
    name: "Winged Classic", category: "Wearable Art", catKey: "Artwork", price: 310,
    image: "/design/helmet-winged-classic.png", alt: "Winged Classic painted helmet",
    href: "#", badge: { label: "1 of 1", tone: "one" },
  },
];

export const footerColumns = [
  {
    title: "Shop",
    links: [
      { label: "Helmets", href: "/shop" },
      { label: "Apparel", href: "/shop" },
      { label: "Wearable Art", href: "/shop" },
      { label: "All products", href: "/shop" },
    ],
  },
  {
    title: "Company",
    links: [
      { label: "About us", href: "/about" },
      { label: "Our story", href: "/about" },
      { label: "Contact", href: "/contact" },
    ],
  },
  {
    title: "Account",
    links: [
      { label: "My account", href: "/account" },
      { label: "My orders", href: "/orders" },
      { label: "Wishlist", href: "/wishlist" },
      { label: "Search", href: "/search" },
    ],
  },
];
