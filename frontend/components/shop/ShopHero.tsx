import Image from "next/image";

/** Shop hero — foggy matessa-pattern backdrop with the section title set large and
 *  right-aligned (per the user's brief). The pattern is blurred + faded so the type
 *  reads cleanly on top. */
export function ShopHero({ title = "Our Products", count }: { title?: string; count?: number }) {
  return (
    <section className="relative overflow-hidden bg-craemy/30">
      {/* Foggy pattern backdrop */}
      <div className="absolute inset-0" aria-hidden="true">
        <Image
          src="/brand/matessa-pattern.webp"
          alt=""
          fill
          priority
          sizes="100vw"
          className="object-cover opacity-25 blur-[3px]"
        />
        <div className="absolute inset-0 bg-gradient-to-r from-white via-white/70 to-transparent" />
      </div>

      <div className="relative mx-auto flex max-w-[1200px] items-center justify-end px-5 py-16 nav:px-8 nav:py-24">
        <div className="text-right">
          <p className="font-body text-sm font-bold uppercase tracking-[0.28em] text-lightgreen">Matessa</p>
          <h1 className="mt-2 font-heading font-bold text-darkgreen [font-size:clamp(46px,7vw,92px)] leading-[0.98]">
            {title}
          </h1>
          {typeof count === "number" && (
            <p className="mt-3 font-body text-sm text-gray-500">
              {count} blend{count === 1 ? "" : "s"} &amp; more, small-batch and single-origin
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
