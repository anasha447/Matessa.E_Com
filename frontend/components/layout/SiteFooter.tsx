import Link from "next/link";
import Image from "next/image";
import { CurvedDivider } from "./CurvedDivider";
import { PillLink } from "@/components/ui/brand";
import { InstagramIcon, FacebookIcon, YoutubeIcon } from "@/components/ui/icons";

const SOCIALS = [
  { label: "Instagram", href: "https://instagram.com", Icon: InstagramIcon },
  { label: "Facebook", href: "https://facebook.com", Icon: FacebookIcon },
  { label: "YouTube", href: "https://youtube.com", Icon: YoutubeIcon },
];

// Pattern texture treatment used on the dark band (verbatim from Landing v4).
const PATTERN_FILTER = "grayscale(1) invert(1) brightness(2.1) sepia(0.55) saturate(1.4)";

const linkStyle: React.CSSProperties = {
  fontFamily: "var(--font-body)",
  fontSize: 15,
  fontWeight: 600,
  color: "#D1D5DB",
  textDecoration: "none",
};

/** Footer variants per the design pages:
 *  `full`    — Landing v4/PDP: curved top (pattern-textured) → quote band → 4 link
 *              columns → bottom bar. `overlap` pulls it up over the previous section
 *              (the landing's dome), exactly like the design's negative band margin.
 *  `cta`     — Our Story: centered logo + "Find Your Blend." + Shop button + slim bar.
 *  `minimal` — Contact: single slim row. */
export function SiteFooter({
  variant = "full",
  overlap = false,
}: {
  variant?: "full" | "cta" | "minimal";
  overlap?: boolean;
}) {
  if (variant === "minimal") {
    return (
      <footer id="footer" style={{ background: "#2F3B28" }}>
        <BottomBar />
      </footer>
    );
  }

  if (variant === "cta") {
    return (
      <footer id="footer">
        <CurvedDivider />
        <div style={{ position: "relative", background: "#2F3B28" }}>
          <PatternLayer />
          <div className="relative mx-auto flex max-w-3xl flex-col items-center gap-6 px-6 py-16 text-center">
            <Image src="/brand/full-logo.webp" alt="" width={152} height={76} className="h-[76px] w-auto" />
            <h2 className="font-heading text-4xl font-bold text-craemy md:text-5xl">Find Your Blend.</h2>
            <PillLink href="/shop" variant="cream">
              Shop Matessa
            </PillLink>
          </div>
          <BottomBar />
        </div>
      </footer>
    );
  }

  return (
    <footer
      id="footer"
      style={{ position: "relative", marginTop: overlap ? "calc(-100vw * 130 / 1440)" : 0 }}
    >
      {/* Curved top with pattern texture + yellow stroke (design geometry) */}
      <div style={{ marginBottom: -1 }}>
        <svg viewBox="0 0 1440 130" aria-hidden="true" style={{ display: "block", width: "100%", height: "auto" }}>
          <defs>
            <pattern id="mtsa-ftp" patternUnits="userSpaceOnUse" width="480" height="480">
              <image href="/brand/matessa-pattern.webp" width="480" height="480" preserveAspectRatio="none" />
            </pattern>
          </defs>
          <path d="M0,100 C460,22 1020,8 1440,84 L1440,131 L0,131 Z" fill="#2F3B28" />
          <path
            d="M0,100 C460,22 1020,8 1440,84 L1440,131 L0,131 Z"
            fill="url(#mtsa-ftp)"
            style={{ opacity: 0.14, filter: PATTERN_FILTER, mixBlendMode: "screen" }}
          />
          <path d="M0,100 C460,22 1020,8 1440,84" fill="none" stroke="#F3CB57" strokeWidth="2.5" />
        </svg>
      </div>

      {/* Quote band */}
      <div style={{ position: "relative", background: "#2F3B28" }}>
        <PatternLayer />
        <div
          aria-hidden="true"
          style={{
            position: "absolute",
            inset: 0,
            background: "linear-gradient(180deg, rgba(47,59,40,0) 40%, rgba(47,59,40,0.92) 100%)",
            pointerEvents: "none",
          }}
        />
        <h2
          className="font-heading"
          style={{
            position: "relative",
            margin: 0,
            padding: "clamp(14px, 2vw, 28px) clamp(20px, 4vw, 48px) clamp(44px, 7vw, 88px)",
            fontWeight: 700,
            fontSize: "clamp(32px, 4.8vw, 60px)",
            lineHeight: 1.25,
            letterSpacing: "-0.01em",
            color: "#F9F7F3",
            textAlign: "center",
            textWrap: "balance",
          }}
        >
          A Sip From Nature, A Taste Of Cultures
        </h2>
      </div>

      {/* Link columns + bottom bar */}
      <div style={{ background: "#2F3B28" }}>
        <div style={{ padding: "0 clamp(20px, 4vw, 48px) 32px" }}>
          <div style={{ maxWidth: 1280, margin: "0 auto", display: "flex", flexDirection: "column", gap: "clamp(44px, 6vw, 64px)" }}>
            {/* Mobile: Shop | Explore (2 cols), Support (full row), then the logo.
                Desktop: logo + three columns in one row. */}
            <div className="grid grid-cols-2 gap-x-8 gap-y-10 border-t border-white/10 pt-9 nav:grid-cols-[auto_1fr_1fr_1fr] nav:gap-11 nav:pt-12">
              <div className="order-last col-span-2 flex justify-center pt-2 nav:order-first nav:col-span-1 nav:justify-start nav:pt-0">
                <Image src="/brand/full-logo.webp" alt="Matessa mate gourd logo" width={240} height={120} className="h-[104px] w-auto nav:h-[120px]" />
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
                className="col-span-2 nav:col-span-1"
                title="Support"
                links={[
                  { label: "Contact Us", href: "/contact" },
                  { label: "Shipping & Returns", href: "/contact" },
                  { label: "Wholesale", href: "/contact" },
                  { label: "FAQ", href: "/product/masala-mate#faq" },
                ]}
              />
            </div>
            <BottomBar bordered />
          </div>
        </div>
      </div>
    </footer>
  );
}

function PatternLayer() {
  return (
    <div
      aria-hidden="true"
      style={{
        position: "absolute",
        inset: 0,
        backgroundImage: "url('/brand/matessa-pattern.webp')",
        backgroundSize: "33.333vw 33.333vw",
        backgroundPosition: "0 -9.028vw",
        opacity: 0.14,
        filter: PATTERN_FILTER,
        mixBlendMode: "screen",
        pointerEvents: "none",
      }}
    />
  );
}

function FooterCol({ title, links, className }: { title: string; links: { label: string; href: string }[]; className?: string }) {
  return (
    <nav aria-label={title} className={className} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
      <h3
        style={{
          margin: 0,
          fontFamily: "var(--font-body)",
          fontSize: 13,
          fontWeight: 700,
          letterSpacing: "0.16em",
          textTransform: "uppercase",
          color: "#EADBA2",
        }}
      >
        {title}
      </h3>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {links.map((l) => (
          <Link
            key={l.label}
            href={l.href}
            className="transition-all duration-300 hover:pl-1 hover:!text-orange-hover"
            style={linkStyle}
          >
            {l.label}
          </Link>
        ))}
      </div>
    </nav>
  );
}

function BottomBar({ bordered = false }: { bordered?: boolean }) {
  return (
    <div
      style={{
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 18,
        borderTop: bordered ? "1px solid rgba(255,255,255,0.10)" : undefined,
        padding: bordered ? "24px 0 0" : "20px clamp(20px, 4vw, 48px)",
        position: "relative",
      }}
    >
      <div style={{ fontFamily: "var(--font-body)", fontSize: 13, fontWeight: 500, color: "rgba(209,213,219,0.7)" }}>
        © 2026 Matessa · matessa.in
      </div>
      <div style={{ display: "flex", gap: 14 }}>
        {SOCIALS.map((s) => (
          <a
            key={s.label}
            href={s.href}
            aria-label={s.label}
            rel="noopener noreferrer"
            target="_blank"
            className="grid h-9 w-9 place-items-center rounded-full text-[#D1D5DB] transition-colors duration-300 hover:bg-white/10 hover:text-orange"
          >
            <s.Icon size={19} />
          </a>
        ))}
      </div>
    </div>
  );
}
