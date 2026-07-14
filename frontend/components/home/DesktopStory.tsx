import Link from "next/link";

/** Cinematic full-bleed story band (desktop): photo + scrim behind the copy.
 *  The media/scrim are CSS-driven (.dk-story-media / .dk-story-scrim). */
export function DesktopStory() {
  return (
    <section className="story dk-story reveal" id="story" aria-label="Our story">
      <div className="dk-story-media" aria-hidden="true" />
      <div className="dk-story-scrim" aria-hidden="true" />
      <div className="dk-story-inner dk-wrap">
        <span className="story-kicker">{"// The Adventure Dose creed"}</span>
        <h2>
          Every piece has a story. <span className="hl">Go write yours.</span>
        </h2>
        <p>
          Gear that&apos;s been there — helmets, adventure apparel, and wearable art, tested on real
          roads and painted by real hands. Pick your kit and go do something.
        </p>
        <Link className="btn btn-primary" href="#">
          Shop the collection
        </Link>
      </div>
    </section>
  );
}
