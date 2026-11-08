// Server-safe admin UI primitives (carried over from ad-admin-ui.jsx, minus the
// count-up/tween animations so they render on the server). Charts that need pointer
// interaction (TrendChart) live in their own client component.
import { formatMoney } from "@/lib/utils/money";

const STATUS_CLASS: Record<string, string> = {
  // OrderStatus
  PENDING: "st-pending",
  PAID: "st-paid",
  SHIPPED: "st-shipped",
  DELIVERED: "st-delivered",
  CANCELLED: "st-cancelled",
  // PaymentStatus
  COMPLETED: "st-success",
  FAILED: "st-danger",
  REFUNDED: "st-warning",
  // ShippingStatus
  RETURNED: "st-neutral",
  // ContactQueryStatus
  OPEN: "st-warning",
  RESOLVED: "st-success",
  // misc
  ACTIVE: "st-success",
  INACTIVE: "st-neutral",
  LOW: "st-warning",
  OUT: "st-danger",
  OK: "st-success",
};

function titleCase(s: string): string {
  return s
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export function StatusBadge({ value, label }: { value: string; label?: string }) {
  return (
    <span className={`st-badge ${STATUS_CLASS[value] ?? "st-neutral"}`}>
      {label ?? titleCase(value)}
    </span>
  );
}

export function EmptyBlock({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <div className="empty-block">
      <div className="wing" />
      <h4>{title}</h4>
      {children && <p>{children}</p>}
    </div>
  );
}

export function Field({
  label,
  error,
  hint,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={`field${error ? " has-err" : ""}`}>
      <label>{label}</label>
      {children}
      {error ? <span className="err">{error}</span> : hint ? <span className="hint">{hint}</span> : null}
    </div>
  );
}

export function KpiCard({
  label,
  value,
  money,
  sub,
  delta,
  accent,
}: {
  label: string;
  value: number;
  money?: boolean;
  sub?: string;
  delta?: number;
  accent?: boolean;
}) {
  const display = money ? formatMoney(value) : value.toLocaleString("en-US");
  return (
    <div className={`kpi${accent ? " is-accent" : ""}`}>
      <div className="k-label">{label}</div>
      <div className="k-value">{display}</div>
      {(sub || delta != null) && (
        <div className="k-sub">
          {delta != null && (
            <span className={`delta ${delta >= 0 ? "up" : "down"}`}>
              {delta >= 0 ? "▲" : "▼"} {Math.abs(delta).toFixed(1)}%
            </span>
          )}
          {sub && <span>{sub}</span>}
        </div>
      )}
    </div>
  );
}

export interface BarRow {
  label: string;
  value: number;
  sub?: string;
  color?: string;
}

export function HBars({ rows, money }: { rows: BarRow[]; money?: boolean }) {
  const max = Math.max(...rows.map((r) => r.value), 1);
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      {rows.map((r) => (
        <div key={r.label}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", marginBottom: 4 }}>
            <span style={{ fontSize: 13, fontWeight: 600 }}>{r.label}</span>
            <span className="mono" style={{ fontSize: 12, color: "var(--text-secondary)" }}>
              {money ? formatMoney(r.value) : r.value.toLocaleString()}
              {r.sub ? ` · ${r.sub}` : ""}
            </span>
          </div>
          <div style={{ height: 8, background: "var(--bg-raised)", borderRadius: 3, overflow: "hidden" }}>
            <div
              style={{
                height: "100%",
                borderRadius: 3,
                background: r.color || "var(--accent)",
                width: `${(r.value / max) * 100}%`,
              }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

export interface DonutSegment {
  label: string;
  value: number;
  color: string;
}

export function Donut({
  segments,
  centerValue,
  centerLabel,
  size = 168,
}: {
  segments: DonutSegment[];
  centerValue: string;
  centerLabel: string;
  size?: number;
}) {
  const total = segments.reduce((s, x) => s + x.value, 0) || 1;
  const R = 70;
  const C = 2 * Math.PI * R;
  // Precompute each segment's fraction + cumulative offset (no render-time mutation).
  const arcs = segments.map((s, i) => ({
    seg: s,
    frac: s.value / total,
    off: segments.slice(0, i).reduce((a, x) => a + x.value / total, 0),
  }));
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 20, flexWrap: "wrap" }}>
      <svg width={size} height={size} viewBox="0 0 180 180" role="img" aria-label={centerLabel}>
        <circle cx="90" cy="90" r={R} fill="none" stroke="var(--bg-raised)" strokeWidth="20" />
        {arcs.map(({ seg: s, frac, off }) => {
          return (
            <circle
              key={s.label}
              cx="90"
              cy="90"
              r={R}
              fill="none"
              stroke={s.color}
              strokeWidth="20"
              strokeDasharray={`${Math.max(0.001, frac * C - 2)} ${C}`}
              strokeDashoffset={-off * C}
              transform="rotate(-90 90 90)"
            />
          );
        })}
        <text x="90" y="86" textAnchor="middle" fontSize="26" fontWeight="700" fontFamily="Barlow Condensed, sans-serif" fill="currentColor">
          {centerValue}
        </text>
        <text x="90" y="104" textAnchor="middle" fontSize="9.5" fontFamily="JetBrains Mono, monospace" fill="currentColor" opacity=".55" style={{ textTransform: "uppercase", letterSpacing: ".08em" }}>
          {centerLabel}
        </text>
      </svg>
      <div className="legend" style={{ flexDirection: "column", gap: 8 }}>
        {segments.map((s) => (
          <span className="li" key={s.label}>
            <span className="sw" style={{ background: s.color }} />
            {s.label} <b className="mono" style={{ fontSize: 11.5, color: "var(--text-primary)" }}>{s.value.toLocaleString()}</b>
          </span>
        ))}
      </div>
    </div>
  );
}
