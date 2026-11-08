"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { icons } from "./icons";

interface NavItem {
  path: string;
  label: string;
  icon: keyof typeof icons;
  exact?: boolean;
}
interface NavGroup {
  section: string;
  adminOnly?: boolean;
  items: NavItem[];
}

const NAV: NavGroup[] = [
  {
    section: "Operate",
    items: [
      { path: "/admin", label: "Dashboard", icon: "dash", exact: true },
      { path: "/admin/orders", label: "Orders", icon: "orders" },
      { path: "/admin/contacts", label: "Contacts", icon: "inbox" },
    ],
  },
  {
    section: "Sell",
    items: [
      { path: "/admin/catalog", label: "Catalog", icon: "catalog" },
      { path: "/admin/promos", label: "Promotions", icon: "promo" },
    ],
  },
  {
    section: "People",
    adminOnly: true,
    items: [
      { path: "/admin/staff", label: "Staff", icon: "staff" },
      { path: "/admin/customers", label: "Customers", icon: "customers" },
    ],
  },
];

export function Sidebar({ isAdmin }: { isAdmin: boolean }) {
  const pathname = usePathname();
  const isActive = (it: NavItem) =>
    it.exact ? pathname === it.path : pathname === it.path || pathname.startsWith(it.path + "/");

  return (
    <aside className="sidebar">
      <div className="sb-brand">
        <div className="sb-logo" />
        <div className="sb-brand-name">
          <b>Adventure Dose</b>
          <small>Admin panel</small>
        </div>
      </div>

      {NAV.filter((g) => !g.adminOnly || isAdmin).map((g) => (
        <div key={g.section}>
          <div className="sb-section">{g.section}</div>
          <nav className="sb-nav" aria-label={g.section}>
            {g.items.map((it) => (
              <Link
                key={it.path}
                href={it.path}
                className={`sb-link${isActive(it) ? " is-active" : ""}`}
                aria-current={isActive(it) ? "page" : undefined}
              >
                {icons[it.icon]}
                <span>{it.label}</span>
              </Link>
            ))}
          </nav>
        </div>
      ))}

      <div className="sb-wing" aria-hidden="true" />
      <div className="sb-foot">
        <Link
          className={`sb-link${pathname.startsWith("/admin/account") ? " is-active" : ""}`}
          href="/admin/account"
          style={{ paddingLeft: 2 }}
        >
          {icons.account}
          <span>Account</span>
        </Link>
        <Link className="sb-link" href="/" style={{ paddingLeft: 2 }}>
          {icons.store}
          <span>View store</span>
        </Link>
        <div className="sb-role-note" style={{ marginTop: 8 }}>
          Signed in · {isAdmin ? "Admin" : "Manager"}
        </div>
      </div>
    </aside>
  );
}
