import Link from "next/link";

const links = [
  { label: "About us", href: "/about" },
  { label: "Contact", href: "/contact" },
  { label: "My orders", href: "/orders" },
];

export function Footer() {
  return (
    <footer className="footer">
      <span className="logo-mark" role="img" aria-label="Adventure Dose logo" />
      <nav>
        {links.map(({ label, href }) => (
          <Link key={label} href={href}>
            {label}
          </Link>
        ))}
      </nav>
      <span>© 2026 Adventure Dose</span>
    </footer>
  );
}
