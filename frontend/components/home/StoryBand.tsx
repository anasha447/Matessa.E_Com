import Link from "next/link";

export function StoryBand() {
  return (
    <section className="story reveal" id="story">
      <div className="wing" />
      <span className="story-kicker">{"// The Adventure Dose creed"}</span>
      <h2>
        Every piece has a story. <span className="hl">Go write yours.</span>
      </h2>
      <p>Gear that&apos;s been there — tested on real roads, painted by real hands.</p>
      <Link href="#">Read the stories →</Link>
    </section>
  );
}
