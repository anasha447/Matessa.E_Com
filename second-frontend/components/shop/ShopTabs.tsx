import Link from "next/link";
import type { CategoryNavItem } from "@/lib/server/catalog";

/** Category filter tabs, rendered as links that set ?category=. Shared by the mobile
 *  and desktop (wide → dk-cattabs) shop layouts. */
export function ShopTabs({
  categories,
  activeId,
  wide = false,
}: {
  categories: CategoryNavItem[];
  activeId?: number;
  wide?: boolean;
}) {
  return (
    <nav className={`cat-tabs${wide ? " dk-cattabs" : ""} reveal`} aria-label="Categories">
      <Link className={`cat-tab${activeId == null ? " is-active" : ""}`} href="/shop">
        All
      </Link>
      {categories.map((c) => (
        <Link
          key={c.id}
          className={`cat-tab${activeId === c.id ? " is-active" : ""}`}
          href={`/shop?category=${c.id}`}
        >
          {c.name}
        </Link>
      ))}
    </nav>
  );
}
