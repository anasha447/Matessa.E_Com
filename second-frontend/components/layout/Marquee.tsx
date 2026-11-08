import { Fragment } from "react";
import { perks } from "@/lib/stubs/home";

interface MarqueeProps {
  items?: string[];
  label?: string;
}

function PerkSet({ items, ariaHidden = false }: { items: string[]; ariaHidden?: boolean }) {
  return (
    <div className="marquee-set" aria-hidden={ariaHidden || undefined}>
      {items.map((item, i) => (
        <Fragment key={`${item}-${i}`}>
          <span>{item}</span>
          <i>{"//"}</i>
        </Fragment>
      ))}
    </div>
  );
}

/** Drifting ticker (CSS-animated; duplicated set for a seamless loop).
 *  Defaults to the store perks; pass `items` for the desktop news ticker. */
export function Marquee({ items = perks, label = "Store perks" }: MarqueeProps = {}) {
  return (
    <div className="marquee" aria-label={label}>
      <div className="marquee-track">
        <PerkSet items={items} />
        <PerkSet items={items} ariaHidden />
      </div>
    </div>
  );
}
