/* eslint-disable @next/next/no-img-element */
import Link from "next/link";
import { Shell } from "@/components/layout/Shell";
import { DesktopHeader } from "@/components/layout/DesktopHeader";
import { DesktopFooter } from "@/components/layout/DesktopFooter";

export const metadata = { title: "About — Adventure Dose" };

const makes = [
  { name: "Helmets", desc: "DOT & ECE certified, painted by hand" },
  { name: "Clothes", desc: "Road-tested, weather-stubborn" },
  { name: "Artwork", desc: "One-of-one shells. When it's gone, it's gone" },
];

export default function AboutPage() {
  const content = (
    <>
      <section className="about-hero">
        <div className="wing" />
        <span className="kicker">{"// About Adventure Dose"}</span>
        <h1>
          Built on the road. <span className="hl">Not in a boardroom.</span>
        </h1>
      </section>

      <section className="about-copy">
        <p>
          <strong>Adventure Dose started with one helmet and one long winter crossing.</strong> The
          helmet took the hit; the story stuck. We&apos;ve been painting, stitching and shipping
          stories ever since.
        </p>
        <p>
          We make three things: helmets that pass real certification, clothes that survive real
          weather, and one-of-one painted art for people who&apos;d rather wear a story than tell
          one. Every piece is tested by riders who genuinely did not take it easy on it.
        </p>
        <p className="aside">
          And yes — you voluntarily opened the About page. That means a lot. Honestly. *helmet tip*
        </p>
      </section>

      <figure className="about-photo">
        <img src="/design/rider-dusk.png" alt="Rider standing on an open road at dusk" />
        <figcaption>{"// Somewhere past the last gas station"}</figcaption>
      </figure>

      <div className="section-heading">
        <h2>What we make</h2>
      </div>
      <nav className="make-grid" aria-label="Categories">
        {makes.map((m) => (
          <Link className="make-tile" href="/shop" key={m.name}>
            <span className="nm">{m.name}</span>
            <span className="ds">{m.desc}</span>
          </Link>
        ))}
      </nav>

      <section className="signoff">
        <div className="small-wing" />
        <p>Every piece has a story. Go write yours.</p>
        <Link className="btn btn-primary" href="/shop">
          Shop the gear
        </Link>
      </section>
    </>
  );

  return (
    <>
      <div className="lg:hidden">
        <Shell>{content}</Shell>
      </div>
      <div className="dk hidden lg:block">
        <DesktopHeader />
        <div className="dk-wrap" style={{ maxWidth: 760 }}>
          {content}
        </div>
        <DesktopFooter />
      </div>
    </>
  );
}
