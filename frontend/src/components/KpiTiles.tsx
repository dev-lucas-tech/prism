import type { Summary } from '../api/types'
import { bps, money } from '../lib/format'

interface Props {
  summary: Summary
}

function Tile({ label, value, sub, accent }: { label: string; value: string; sub?: string; accent?: boolean }) {
  return (
    <div className={`rounded-xl border p-4 ${accent ? 'border-rose-200 bg-rose-50' : 'border-slate-200 bg-white'}`}>
      <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</div>
      <div className={`mt-1 text-2xl font-semibold ${accent ? 'text-rose-700' : 'text-slate-900'}`}>{value}</div>
      {sub && <div className="mt-1 text-xs text-slate-500">{sub}</div>}
    </div>
  )
}

export default function KpiTiles({ summary }: Props) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <Tile label="Total spend" value={money(summary.totalSpend)} sub="settled, this period" />
      <Tile label="FX leakage" value={money(summary.totalHiddenFxCost)} sub="hidden markup paid" accent />
      <Tile label="Effective markup" value={bps(summary.effectiveMarkupBps)} sub="over mid-market" />
      <Tile label="Stablecoin savings" value={money(summary.totalStablecoinSavings)} sub="vs. card FX path" />
    </div>
  )
}
