import type { Transaction } from '../api/types'
import { bps, money } from '../lib/format'

interface Props {
  live: Transaction[]
}

export default function LiveFeed({ live }: Props) {
  if (live.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 p-4 text-sm text-slate-400">
        No live transactions yet — simulate a purchase to watch it settle.
      </div>
    )
  }
  return (
    <div className="space-y-2">
      {live.map((t) => {
        const settled = t.status === 'SETTLED'
        return (
          <div
            key={t.id}
            className={`rounded-lg border p-3 text-sm transition-colors ${
              settled ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="font-medium text-slate-900">
                {t.merchant} · {t.corridor}
              </span>
              <span className="text-xs text-slate-500">
                {t.purchaseAmount.toLocaleString()} {t.corridor}
              </span>
            </div>
            {settled ? (
              <div className="mt-1 text-emerald-800">
                Settled — {money(t.settledCost)} · hidden {money(t.hiddenFxCost)} · {bps(t.effectiveMarkupBps)}
              </div>
            ) : (
              <div className="mt-1 text-amber-800">
                Pending — est. {money(t.estimatedCost)}{' '}
                <span className="text-xs text-amber-700/70">(final FX locks at settlement)</span>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
