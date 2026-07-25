"use client";

// Orders over time — bucketed client-side from recent orders' createdAt so the admin can
// flip between a week / month / 6 months / year and see the trend rise or fall.
import { useMemo, useRef, useState } from "react";

type Period = "week" | "month" | "6m" | "year";
const PERIODS: { key: Period; label: string }[] = [
  { key: "week", label: "Week" },
  { key: "month", label: "Month" },
  { key: "6m", label: "6 Months" },
  { key: "year", label: "Year" },
];
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

interface Bucket {
  label: string;
  full: string;
  count: number;
}

function buildBuckets(period: Period, timestamps: string[]): Bucket[] {
  const now = new Date();
  const spans: { start: number; end: number; label: string; full: string }[] = [];

  if (period === "week" || period === "month") {
    const days = period === "week" ? 7 : 30;
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
      const next = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1);
      spans.push({
        start: d.getTime(),
        end: next.getTime(),
        label: `${d.getDate()}`,
        full: d.toLocaleDateString("en-IN", { day: "numeric", month: "short" }),
      });
    }
  } else {
    const months = period === "6m" ? 6 : 12;
    for (let i = months - 1; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      const next = new Date(d.getFullYear(), d.getMonth() + 1, 1);
      spans.push({
        start: d.getTime(),
        end: next.getTime(),
        label: MONTHS[d.getMonth()],
        full: `${MONTHS[d.getMonth()]} ${d.getFullYear()}`,
      });
    }
  }

  const counts = new Array(spans.length).fill(0);
  for (const iso of timestamps) {
    const t = new Date(iso).getTime();
    if (Number.isNaN(t)) continue;
    for (let i = 0; i < spans.length; i++) {
      if (t >= spans[i].start && t < spans[i].end) {
        counts[i]++;
        break;
      }
    }
  }
  return spans.map((s, i) => ({ label: s.label, full: s.full, count: counts[i] }));
}

export function OrdersOverTimeChart({ timestamps }: { timestamps: string[] }) {
  const [period, setPeriod] = useState<Period>("month");
  const [tip, setTip] = useState<{ i: number; left: number; top: number } | null>(null);
  const wrapRef = useRef<HTMLDivElement>(null);

  const buckets = useMemo(() => buildBuckets(period, timestamps), [period, timestamps]);
  const total = buckets.reduce((s, b) => s + b.count, 0);
  const max = Math.max(...buckets.map((b) => b.count), 1);

  const W = 760;
  const H = 240;
  const padL = 34;
  const padR = 12;
  const padT = 14;
  const padB = 26;
  const n = buckets.length;
  const bandW = (W - padL - padR) / n;
  const barW = Math.max(3, Math.min(bandW * 0.62, 46));
  const y = (v: number) => padT + (1 - v / (max * 1.1)) * (H - padT - padB);
  // for dense daily views, only label a few ticks
  const labelEvery = n > 14 ? Math.ceil(n / 8) : 1;

  const onMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = wrapRef.current?.getBoundingClientRect();
    if (!rect) return;
    const relX = ((e.clientX - rect.left) / rect.width) * W - padL;
    const i = Math.max(0, Math.min(n - 1, Math.floor(relX / bandW)));
    const cx = padL + i * bandW + bandW / 2;
    setTip({ i, left: (cx / W) * rect.width, top: (y(buckets[i].count) / H) * rect.height });
  };

  return (
    <div>
      <div className="filter-row" style={{ marginBottom: 12 }} role="group" aria-label="Time range">
        {PERIODS.map((p) => (
          <button
            key={p.key}
            type="button"
            className={`f-chip${period === p.key ? " is-active" : ""}`}
            aria-pressed={period === p.key}
            onClick={() => {
              setPeriod(p.key);
              setTip(null);
            }}
          >
            {p.label}
          </button>
        ))}
        <span className="mono" style={{ marginLeft: "auto", fontSize: 12.5, color: "var(--text-secondary)", alignSelf: "center" }}>
          {total.toLocaleString()} order{total === 1 ? "" : "s"} in this range
        </span>
      </div>

      {total === 0 ? (
        <p style={{ color: "var(--text-secondary)", fontSize: 13, padding: "28px 0" }}>No orders in this range yet.</p>
      ) : (
        <div ref={wrapRef} style={{ position: "relative" }} onMouseMove={onMove} onMouseLeave={() => setTip(null)}>
          <svg viewBox={`0 0 ${W} ${H}`} style={{ width: "100%", display: "block" }} role="img" aria-label={`Orders over the selected ${period}`}>
            {Array.from({ length: 4 }, (_, i) => {
              const gy = padT + (i / 3) * (H - padT - padB);
              const val = Math.round(max * 1.1 * (1 - i / 3));
              return (
                <g key={i}>
                  <line x1={padL} x2={W - padR} y1={gy} y2={gy} stroke="currentColor" opacity=".09" strokeWidth="1" />
                  <text x={padL - 7} y={gy + 4} textAnchor="end" fontSize="10.5" fontFamily="var(--font-mono)" fill="currentColor" opacity=".5">
                    {val}
                  </text>
                </g>
              );
            })}
            {buckets.map((b, i) => {
              const cx = padL + i * bandW + bandW / 2;
              const by = y(b.count);
              const active = tip?.i === i;
              return (
                <g key={i}>
                  <rect
                    x={cx - barW / 2}
                    y={by}
                    width={barW}
                    height={Math.max(0, H - padB - by)}
                    rx={Math.min(3, barW / 2)}
                    fill={active ? "var(--accent-hover)" : "var(--accent)"}
                  />
                  {i % labelEvery === 0 && (
                    <text x={cx} y={H - 8} textAnchor="middle" fontSize="10" fontFamily="var(--font-mono)" fill="currentColor" opacity=".5">
                      {b.label}
                    </text>
                  )}
                </g>
              );
            })}
          </svg>
          {tip && (
            <div className="chart-tip" style={{ left: tip.left, top: tip.top }}>
              {buckets[tip.i].full} · <b>{buckets[tip.i].count}</b>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
