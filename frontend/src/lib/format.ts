const DASH = '—'

export function money(n: number | null | undefined, ccy = 'USD'): string {
  if (n === null || n === undefined) return DASH
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: ccy }).format(n)
}

export function bps(n: number | null | undefined): string {
  if (n === null || n === undefined) return DASH
  return `${Math.round(n)} bps`
}

export function pct(n: number | null | undefined): string {
  if (n === null || n === undefined) return DASH
  return `${(n * 100).toFixed(2)}%`
}
