"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  BagIcon,
  HeartIcon,
  HomeIcon,
  SearchIcon,
  UserIcon,
} from "@/components/ui/icons";
import type { ComponentType } from "react";

interface Tab {
  label: string;
  href: string;
  Icon: ComponentType<{ size?: number }>;
}

const tabs: Tab[] = [
  { label: "Home", href: "/", Icon: HomeIcon },
  { label: "Shop", href: "/shop", Icon: BagIcon },
  { label: "Search", href: "/search", Icon: SearchIcon },
  { label: "Wishlist", href: "/wishlist", Icon: HeartIcon },
  { label: "Account", href: "/account", Icon: UserIcon },
];

/** Fixed bottom navigation; highlights the active route. */
export function TabBar() {
  const pathname = usePathname();
  return (
    <nav className="tabbar" aria-label="Primary">
      {tabs.map(({ label, href, Icon }) => {
        const active = href === "/" ? pathname === "/" : pathname === href;
        return (
          <Link
            key={label}
            className={`tab${active ? " is-active" : ""}`}
            href={href}
            aria-current={active ? "page" : undefined}
          >
            <Icon />
            {label}
          </Link>
        );
      })}
    </nav>
  );
}
