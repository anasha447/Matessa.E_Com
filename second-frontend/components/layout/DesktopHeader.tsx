import Link from "next/link";
import { HeartIcon, SearchIcon, UserIcon } from "@/components/ui/icons";
import { CartButton } from "@/components/cart/CartButton";
import { desktopNav } from "@/lib/stubs/home-desktop";

/** Desktop top bar: primary nav (left) · centered logo · actions (right). */
export function DesktopHeader() {
  return (
    <header className="dk-topbar">
      <nav className="dk-nav" aria-label="Primary">
        {desktopNav.map((item) => (
          <Link key={item.label} href={item.href}>
            {item.label}
          </Link>
        ))}
      </nav>
      <Link className="dk-logo" href="/" aria-label="Adventure Dose home">
        <span className="logo-mark" role="img" aria-label="Adventure Dose logo" />
      </Link>
      <div className="dk-actions">
        <Link className="topbar-action" href="#" aria-label="Search">
          <SearchIcon size={22} />
        </Link>
        <Link className="topbar-action" href="/account" aria-label="Account">
          <UserIcon size={22} />
        </Link>
        <Link className="topbar-action" href="/wishlist" aria-label="Wishlist">
          <HeartIcon size={22} />
        </Link>
        <CartButton />
      </div>
    </header>
  );
}
