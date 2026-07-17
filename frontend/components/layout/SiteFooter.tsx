import Link from "next/link";
import Image from "next/image";
import { CurvedDivider } from "./CurvedDivider";
import { PillLink } from "@/components/ui/brand";

/** Footer variants per the design pages:
 *  `full`    — v4/PDP: curved divider → quote band → 4 link columns → bottom bar.
 *  `cta`     — Our Story: centered logo + "Find Your Blend." + Shop button + slim bar.
 *  `minimal` — Contact: single slim row. */
export function SiteFooter({ variant = "full" }: { variant?: "full" | "cta" | "minimal" }) {
  if (variant === "minimal") {
    return (
      <footer className="bg-darkgreen">
        <BottomBar />
      </footer>
    );
  }

  if (variant === "cta") {
    return (
      <footer>
        <CurvedDivider />
        <div className="bg-darkgreen bg-blend-soft-light [background-image:url('/brand/matessa-pattern.webp')] [background-size:420px]">
          <div className="mx-auto flex max-w-3xl flex-col items-center gap-6 px-6 py-16 text-center">
            <Image src="/brand/full-logo.webp" alt="" width={152} height={76} className="h-[76px] w-auto" />
            <h2 className="font-heading font-bold text-craemy text-4xl md:text-5xl">Find Your Blend.</h2>
            <PillLink href="/shop" variant="cream">
              Shop Matessa
            </PillLink>
          </div>
          <BottomBar inline />
        </div>
      </footer>
    );
  }

  return (
    <footer>
      <CurvedDivider />
      <div className="bg-darkgreen bg-blend-soft-light [background-image:url('/brand/matessa-pattern.webp')] [background-size:420px]">
        {/* Quote band */}
        <div className="mx-auto max-w-5xl px-6 pb-4 pt-10 text-center">
          <h2 className="font-heading font-bold text-craemy [font-size:clamp(32px,4.8vw,60px)] leading-tight text-balance">
            A Sip From Nature, A Taste Of Cultures
          </h2>
        </div>

        {/* Link columns */}
        <div className="mx-auto grid max-w-[1200px] grid-cols-2 gap-10 px-6 py-12 md:grid-cols-[auto_1fr_1fr_1fr] md:gap-14">
          <div className="col-span-2 md:col-span-1">
            <Image src="/brand/full-logo.webp" alt="Matessa" width={240} height={120} className="h-[120px] w-auto" />
          </div>
          <FooterCol
            title="Shop"
            links={[
              { label: "Masala Mate", href: "/product/masala-mate" },
              { label: "Lemon & Ginger", href: "/product/lemon-ginger-mate" },
              { label: "Raw Mate", href: "/product/raw-mate" },
              { label: "Gourds & Bombillas", href: "/shop" },
            ]}
          />
          <FooterCol
            title="Explore"
            links={[
              { label: "What is mate?!", href: "/what-is-mate" },
              { label: "Health Benefits", href: "/#health" },
              { label: "Our Story", href: "/about" },
              { label: "Who Drinks Mate?", href: "/#gallery" },
            ]}
          />
          <FooterCol
            title="Support"
            links={[
              { label: "Contact Us", href: "/contact" },
              { label: "Track Order", href: "/track" },
              { label: "Wholesale", href: "/contact" },
              { label: "FAQ", href: "/product/masala-mate#faq" },
            ]}
          />
        </div>

        <BottomBar inline />
      </div>
    </footer>
  );
}

function FooterCol({ title, links }: { title: string; links: { label: string; href: string }[] }) {
  return (
    <nav aria-label={title}>
      <h3 className="mb-4 font-heading font-bold text-lg text-craemy">{title}</h3>
      <ul className="space-y-2.5">
        {links.map((l) => (
          <li key={l.label}>
            <Link
              href={l.href}
              className="font-body text-gray-300 transition-all duration-300 hover:pl-1 hover:text-orange"
            >
              {l.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}

function BottomBar({ inline = false }: { inline?: boolean }) {
  return (
    <div className={`${inline ? "" : "py-2"} border-t border-white/10`}>
      <div className="mx-auto flex max-w-[1200px] flex-col items-center justify-between gap-3 px-6 py-5 sm:flex-row">
        <p className="font-body text-sm text-gray-300">© 2026 Matessa · matessa.in</p>
        <div className="flex items-center gap-5 font-body text-sm text-gray-300">
          <a href="https://instagram.com" rel="noopener noreferrer" target="_blank" className="transition-colors hover:text-orange">
            Instagram
          </a>
          <a href="https://facebook.com" rel="noopener noreferrer" target="_blank" className="transition-colors hover:text-orange">
            Facebook
          </a>
          <a href="https://youtube.com" rel="noopener noreferrer" target="_blank" className="transition-colors hover:text-orange">
            YouTube
          </a>
        </div>
      </div>
    </div>
  );
}
