import { FilterIcon, SortIcon } from "@/components/ui/icons";

const pad = (n: number) => `${n < 10 ? "0" : ""}${n}`;

/** Result count + Filter/Sort entry points. The chips are placeholders for now
 *  (filter/sort modals come later); the count is live. */
export function FilterBar({ count }: { count: number }) {
  return (
    <div className="filter-bar">
      <span className="result-count">
        {pad(count)} item{count === 1 ? "" : "s"}
      </span>
      <div className="filter-actions">
        <button className="chip-btn" type="button">
          <FilterIcon />
          Filter
        </button>
        <button className="chip-btn" type="button">
          <SortIcon />
          Sort
        </button>
      </div>
    </div>
  );
}
