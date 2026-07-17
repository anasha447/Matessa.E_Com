import Image from "next/image";

/** PDP culture band (design: Product Page.dc.html §Culture) — dark-green card with the
 *  matessa-pattern texture, yellow Asul heading + copy, and a lifestyle image. */
export function CultureBand() {
  return (
    <section className="mx-auto max-w-[1200px] px-5 py-12 nav:px-8">
      <div className="relative overflow-hidden rounded-[2rem] bg-darkgreen">
        <div
          aria-hidden="true"
          className="absolute inset-0 opacity-15"
          style={{
            backgroundImage: "url('/brand/matessa-pattern.webp')",
            backgroundSize: "360px",
            mixBlendMode: "screen",
          }}
        />
        <div className="relative flex flex-col items-center gap-8 p-8 nav:flex-row nav:p-12">
          <div className="flex-1">
            <h2 className="font-heading font-bold text-yellow [font-size:clamp(28px,3.4vw,46px)] leading-[1.16]">
              Good Things Are Growing In The Shade
            </h2>
            <p className="mt-4 max-w-[48ch] font-body text-[15px] font-medium leading-[1.75] text-craemy/90 nav:text-[17px]">
              Long before energy drinks, there was mate. For centuries this has been the daily natural fuel of South
              America, yet it remains a secret here in Asia. Be among the first to discover the forest&apos;s ancient
              answer to lasting energy and focus.
            </p>
          </div>
          <div className="w-full flex-1">
            <Image
              src="/brand/story-banner.webp"
              alt="Yerba mate growing in the shade"
              width={620}
              height={420}
              className="h-full w-full rounded-[1.5rem] object-cover"
            />
          </div>
        </div>
      </div>
    </section>
  );
}
