import { redirect } from "next/navigation";
import Link from "next/link";
import { Shell } from "@/components/layout/Shell";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";
import { LogoutButton } from "@/components/account/LogoutButton";
import { ThemeToggle } from "@/components/account/ThemeToggle";
import { getAccessToken, getCurrentUser, serverGet } from "@/lib/server/session";
import type { AddressResponse, Page } from "@/lib/api";

export const dynamic = "force-dynamic";

const chev = (
  <svg className="chev" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="m9 6 6 6-6 6" />
  </svg>
);

export default async function AccountPage() {
  const token = await getAccessToken();
  if (!token) redirect("/login?next=/account");

  const [user, addresses] = await Promise.all([
    getCurrentUser(),
    serverGet<Page<AddressResponse>>("/api/users/me/addresses"),
  ]);
  const name = user ? `${user.firstName} ${user.lastName}`.trim() : "Rider";
  const addrCount = addresses?.page.totalElements ?? 0;

  const content = (
    <>
      <section className="account-head" aria-label="Profile">
        <div className="avatar">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="8" r="4" />
            <path d="M4 21a8 8 0 0 1 16 0" />
          </svg>
        </div>
        <div className="who">
          <div className="name">{name}</div>
          <div className="email">{user?.email}</div>
        </div>
        <Link className="edit" href="/account/profile">
          Edit
        </Link>
      </section>

      <div className="group-label">Orders &amp; saved info</div>
      <div className="row-group">
        <Link className="row-item" href="/orders">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 8v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8" />
            <path d="M1 3h22v5H1z" />
            <path d="M10 12h4" />
          </svg>
          <span className="grow">My orders</span>
          {chev}
        </Link>
        <Link className="row-item" href="/account/addresses">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0Z" />
            <circle cx="12" cy="10" r="3" />
          </svg>
          <span className="grow">Saved addresses</span>
          <span className="meta">
            {addrCount} saved
          </span>
          {chev}
        </Link>
        <Link className="row-item" href="/wishlist">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M19.8 5.6a5 5 0 0 0-7.1 0L12 6.3l-.7-.7a5 5 0 1 0-7.1 7.1l7.8 7.8 7.8-7.8a5 5 0 0 0 0-7.1Z" />
          </svg>
          <span className="grow">Wishlist</span>
          {chev}
        </Link>
      </div>

      <div className="group-label">Preferences</div>
      <div className="row-group">
        <div className="row-item">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
          </svg>
          <span className="grow">Dusk mode</span>
          <ThemeToggle />
        </div>
      </div>

      <div className="group-label">Support</div>
      <div className="row-group">
        <Link className="row-item" href="/contact">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 8.6 8.6 0 0 1-3.7-.8L3 20l1-5a8.4 8.4 0 1 1 17-3.5Z" />
          </svg>
          <span className="grow">Contact us</span>
          {chev}
        </Link>
      </div>

      <div className="logout-wrap">
        <LogoutButton />
      </div>
    </>
  );

  return (
    <>
      <div className="lg:hidden">
        <Shell>{content}</Shell>
      </div>
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap" style={{ maxWidth: 680 }}>
          {content}
        </div>
        <DesktopFooter />
      </div>
    </>
  );
}
