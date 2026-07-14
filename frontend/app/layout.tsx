import type { Metadata } from "next";
import { Barlow, Barlow_Condensed, JetBrains_Mono } from "next/font/google";
import "./globals.css";
import "./adventure.css";
import "./pages.css";
import { cookies } from "next/headers";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/session/cookies";
import { CartProvider } from "@/components/cart/CartContext";
import { CartDrawer } from "@/components/cart/CartDrawer";
import { NewsletterPopup } from "@/components/newsletter/NewsletterPopup";

// Google fonts → CSS variables consumed by the design tokens in adventure.css
// (--font-body, --font-action, --font-mono). Sprintura/Magnetar (display) are
// self-hosted via @font-face in adventure.css.
const barlow = Barlow({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-barlow",
});
const barlowCondensed = Barlow_Condensed({
  subsets: ["latin"],
  weight: ["500", "600", "700"],
  variable: "--font-barlow-condensed",
});
const jetBrainsMono = JetBrains_Mono({
  subsets: ["latin"],
  variable: "--font-jetbrains",
});

export const metadata: Metadata = {
  title: "Adventure Dose — Home",
  description:
    "Outdoor & adventure gear — helmets, clothes, and artwork. Every piece has a story.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // "Logged in" for cart purposes = a session cookie is present (refresh survives access
  // expiry). Drives server-cart vs guest-cart mode and the merge-on-login in CartProvider.
  const jar = await cookies();
  const authed = jar.has(ACCESS_COOKIE) || jar.has(REFRESH_COOKIE);
  return (
    <html
      lang="en"
      data-theme="dark"
      className={`${barlow.variable} ${barlowCondensed.variable} ${jetBrainsMono.variable}`}
    >
      {/* suppressHydrationWarning: browser extensions (e.g. ColorZilla's
          cz-shortcut-listen) inject attributes onto <body> before React hydrates,
          which is harmless but otherwise logs a hydration mismatch. */}
      <body suppressHydrationWarning>
        <CartProvider authed={authed}>
          {children}
          <CartDrawer />
          <NewsletterPopup guest={!authed} />
        </CartProvider>
      </body>
    </html>
  );
}
