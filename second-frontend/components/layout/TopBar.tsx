import Link from "next/link";
import { MenuIcon } from "@/components/ui/icons";
import { CartButton } from "@/components/cart/CartButton";

/** Sticky top bar: menu · centered logo · search + cart. */
export function TopBar() {
  return (
    <header className="topbar topbar-centered">
      <button className="topbar-action" type="button" aria-label="Menu">
        <MenuIcon />
      </button>
      <Link className="logo-center" href="/" aria-label="Adventure Dose home">
        <span className="logo-mark" role="img" aria-label="Adventure Dose logo" />
      </Link>
      <div className="topbar-right">
        <CartButton />
      </div>
    </header>
  );
}
