import { Fragment } from "react";
import Link from "next/link";
import { footerColumns } from "@/lib/stubs/home-desktop";

const barPerks = ["Free shipping over $99", "30-day returns", "DOT & ECE certified"];

/** Desktop multi-column footer + bottom bar. */
export function DesktopFooter() {
  return (
    <footer className="dk-footer">
      <div className="dk-footer-inner">
        <div className="dk-foot-brand">
          <div className="logo-mark" role="img" aria-label="Adventure Dose logo" />
          <p>
            Gear that&apos;s been there. Helmets, apparel, and wearable art — each piece tied to a
            story and built to ride.
          </p>
        </div>
        {footerColumns.map((col) => (
          <nav key={col.title} className="dk-foot-col" aria-label={col.title}>
            <h3>{col.title}</h3>
            {col.links.map((link) => (
              <Link key={link.label} href={link.href}>
                {link.label}
              </Link>
            ))}
          </nav>
        ))}
      </div>
      <div className="dk-footer-bar">
        <span>© 2026 Adventure Dose</span>
        <span>
          {barPerks.map((perk, i) => (
            <Fragment key={perk}>
              {i > 0 && <i>{" // "}</i>}
              {perk}
            </Fragment>
          ))}
        </span>
      </div>
    </footer>
  );
}
