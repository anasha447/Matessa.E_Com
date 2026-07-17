// "Better Than Tea. Smarter Than Coffee." (design: Matessa Landing v4 §7).
// Hand-authored SVG line chart (curves lifted from the design) + three comparison
// cards; Yerba Mate is the highlighted winner. Pure server component — no JS.

import { Reveal } from "@/components/Reveal";

const CARDS = [
  {
    name: "Coffee",
    copy: "Quick spike followed by an energy crash within 2–3 hours. Often accompanied by jitters and anxiety.",
    highlight: false,
  },
  {
    name: "Tea",
    copy: "Gentler lift but lower energy levels. May not provide enough sustained focus for demanding tasks.",
    highlight: false,
  },
  {
    name: "Yerba Mate",
    copy: "Smooth, steady energy for 4–6 hours. Sharp focus with no crash and no jitters.",
    highlight: true,
  },
];

export function EnergyComparison() {
  return (
    <section id="energy" aria-label="Energy Comparison" className="py-16 nav:py-24">
      <div className="mx-auto max-w-[1100px] px-6">
        <Reveal>
          <h2 className="mb-12 text-center font-heading text-3xl font-bold text-darkgreen md:text-4xl">
            Better Than Tea.
            <br />
            Smarter Than Coffee.
          </h2>
        </Reveal>

        <div className="flex flex-col-reverse items-center gap-8 nav:flex-row-reverse nav:items-stretch nav:gap-12">
          {/* Chart card */}
          <Reveal className="w-full nav:w-1/2">
            <figure className="h-full rounded-[2rem] bg-[#FCF0E3] p-6 nav:p-8">
              <figcaption className="mb-4 text-center font-heading text-lg font-bold text-darkgreen">
                Energy Over Time
              </figcaption>
              <svg viewBox="0 0 400 300" role="img" aria-label="Line chart: coffee spikes then crashes, tea stays low, mate holds a steady plateau" className="w-full">
                {/* axes */}
                <line x1="20" y1="280" x2="390" y2="280" stroke="#2F3B28" strokeWidth="1.5" opacity="0.35" />
                <line x1="20" y1="280" x2="20" y2="20" stroke="#2F3B28" strokeWidth="1.5" opacity="0.35" />
                {/* curves (from the design) */}
                <path d="M 20 280 C 50 150, 100 50, 120 80 C 140 110, 180 250, 320 275" fill="none" stroke="#EF4444" strokeWidth="3" strokeDasharray="7 6" strokeLinecap="round" />
                <path d="M 20 280 C 60 220, 120 150, 160 150 C 200 150, 280 200, 350 230" fill="none" stroke="#F3CB57" strokeWidth="3" strokeDasharray="7 6" strokeLinecap="round" />
                <path d="M 20 280 C 50 200, 80 100, 140 100 L 260 100 C 300 100, 340 120, 380 150" fill="none" stroke="#3E5F2D" strokeWidth="3.5" strokeLinecap="round" />
                {/* axis labels */}
                <text x="8" y="150" transform="rotate(-90 8 150)" textAnchor="middle" fontSize="12" fill="#4A5545" fontFamily="var(--font-body)">
                  Energy Level
                </text>
                <text x="205" y="298" textAnchor="middle" fontSize="12" fill="#4A5545" fontFamily="var(--font-body)">
                  Time (Hours)
                </text>
              </svg>
              <div className="mt-4 flex items-center justify-center gap-5 font-body text-sm text-gray-600">
                <span className="flex items-center gap-2"><i className="h-1 w-6 rounded bg-[#EF4444]" /> Coffee</span>
                <span className="flex items-center gap-2"><i className="h-1 w-6 rounded bg-yellow" /> Tea</span>
                <span className="flex items-center gap-2"><i className="h-1 w-6 rounded bg-green" /> Mate</span>
              </div>
            </figure>
          </Reveal>

          {/* Comparison cards */}
          <div className="flex w-full flex-col gap-4 nav:w-1/2">
            {CARDS.map((c, i) => (
              <Reveal key={c.name} delay={i * 110}>
                <div
                  className={`rounded-[18px] border-2 p-5 ${
                    c.highlight ? "border-green bg-[#E8F3E8]" : "border-gray-200 bg-white"
                  }`}
                >
                  <h3 className={`mb-1.5 font-heading text-lg font-bold ${c.highlight ? "text-green" : "text-darkgreen"}`}>
                    {c.name}
                  </h3>
                  <p className="text-[15px] text-gray-600">{c.copy}</p>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
