import { SiteHeader } from "@/components/layout/SiteHeader";
import { SiteFooter } from "@/components/layout/SiteFooter";

/** Landing page — sections land here in Phase 2 (design: Matessa Landing v4). */
export default function HomePage() {
  return (
    <>
      <SiteHeader variant="overlay" />
      <main className="min-h-[70svh] bg-white pt-24 nav:pt-32">
        <div className="mx-auto max-w-3xl px-6 text-center">
          <h1 className="font-heading font-bold text-darkgreen [font-size:clamp(42px,5.2vw,74px)] leading-[1.06] tracking-tight text-balance">
            The Ritual of <em className="italic text-green">Natural Energy</em>
          </h1>
          <p className="mt-6 text-gray-600">Landing sections are being brewed (Phase 2).</p>
        </div>
      </main>
      <SiteFooter variant="full" />
    </>
  );
}
