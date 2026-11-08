// Money is sent as 2-dp JSON numbers (server-side BigDecimal). Per API.md §3 we never
// do money arithmetic on the client — totals always come from the server. This helper
// is display-only.
export function formatMoney(amount: number, currency = 'USD', locale = 'en-US'): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

/** Compact money for chart axes / tight labels, e.g. $1.2k, $3.4M. */
export function formatMoneyShort(amount: number): string {
  const abs = Math.abs(amount);
  if (abs >= 1_000_000) return `$${(amount / 1_000_000).toFixed(1)}M`;
  if (abs >= 1_000) return `$${(amount / 1_000).toFixed(1)}k`;
  return `$${Math.round(amount)}`;
}
