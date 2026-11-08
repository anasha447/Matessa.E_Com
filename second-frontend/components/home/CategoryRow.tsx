import Link from "next/link";
import { SectionHeading } from "./SectionHeading";
import { categories as stubCategories, type CategoryTile } from "@/lib/stubs/home";

export function CategoryRow({ categories = stubCategories }: { categories?: CategoryTile[] }) {
  return (
    <>
      <SectionHeading title="Shop by category" />
      <nav className="cat-row reveal" aria-label="Categories">
        {categories.map((cat) => (
          <Link key={cat.name} className="cat-tile" href={cat.href}>
            <span className="count">{cat.count} items</span>
            <span className="name">{cat.name}</span>
          </Link>
        ))}
      </nav>
    </>
  );
}
