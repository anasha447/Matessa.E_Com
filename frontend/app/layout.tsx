import type { Metadata } from "next";
import { Asul, Quicksand } from "next/font/google";
import "./globals.css";
import "./matessa.css";
import { cookies } from "next/headers";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/session/cookies";
import { CartProvider } from "@/components/cart/CartContext";

// Matessa brand fonts (BRAND.md §3) → --font-asul / --font-quicksand, consumed by
// the @theme block in globals.css and the tokens in matessa.css.
const asul = Asul({
  subsets: ["latin"],
  weight: ["400", "700"],
  variable: "--font-asul",
});
const quicksand = Quicksand({
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700"],
  variable: "--font-quicksand",
});

export const metadata: Metadata = {
  title: "Matessa — A Sip From Nature, A Taste Of Cultures",
  description:
    "Premium yerba mate for India — Masala, Lemon & Ginger and Raw loose-leaf blends, plus gourds and bombillas. Natural, focused energy from matessa.in.",
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
    <html lang="en" className={`${asul.variable} ${quicksand.variable}`}>
      {/* suppressHydrationWarning: browser extensions inject attributes onto <body>
          before React hydrates — harmless, but otherwise logs a mismatch. */}
      <body suppressHydrationWarning>
        <CartProvider authed={authed}>{children}</CartProvider>
      </body>
    </html>
  );
}
