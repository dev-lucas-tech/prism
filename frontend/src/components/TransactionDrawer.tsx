import type { Transaction } from '../api/types'
import { bps, money, rate } from '../lib/format'

interface Props {
  tx: Transaction
  onClose: () => void
}

function Row({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className="flex items-center justify-between py-1.5">
      <span className="text-sm text-slate-500">{label}</span>
      <span className={`text-sm font-medium tabular-nums ${accent ? 'text-rose-600' : 'text-slate-900'}`}>
        {value}
      </span>
    </div>
  )
}

export default function TransactionDrawer({ tx, onClose }: Props) {
  const amountCharged = tx.settledCost ?? tx.estimatedCost
  const conversionRate = tx.purchaseAmount ? amountCharged / tx.purchaseAmount : null

  return (
    <div className="fixed inset-0 z-20 flex justify-end">
      <div className="absolute inset-0 bg-slate-900/30" onClick={onClose} aria-hidden="true" />
      <aside className="relative z-30 flex h-full w-full max-w-md flex-col overflow-y-auto bg-white p-6 shadow-xl">
        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">{tx.merchant}</h2>
            <p className="text-sm text-slate-500">
              {tx.merchantCountry} · {tx.corridor} · {tx.employee}
            </p>
          </div>
          <button
            onClick={onClose}
            className="rounded p-1 text-slate-400 hover:bg-slate-100"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        <div className="mt-4 rounded-lg border border-slate-200 p-4">
          <div className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-500">
            Default View
          </div>
          <Row label="Status" value={tx.status} />
          <Row label="Merchant charge" value={`${tx.purchaseAmount.toLocaleString()} ${tx.corridor}`} />
          <Row label="Amount charged" value={money(amountCharged)} />
          <Row label="Conversion rate" value={`${rate(conversionRate)} / ${tx.corridor}`} />
          <Row label="Card" value={`•••• 1234 · ${tx.employee}`} />
        </div>

        <div className="mt-4 rounded-lg border border-indigo-200 bg-indigo-50 p-4">
          <div className="mb-1 text-xs font-medium uppercase tracking-wide text-indigo-700">
            Showing Hidden Fees
          </div>
          <Row label="Mid-market reference" value={money(tx.midMarketCost)} />
          <Row label="Hidden FX cost" value={money(tx.hiddenFxCost)} accent />
          <Row label="Effective markup" value={bps(tx.effectiveMarkupBps)} />
          <Row label="Estimated at swipe (mid)" value={money(tx.estimatedCost)} />
          <Row label="Surprise vs. estimate" value={money(tx.surpriseVsEstimate)} accent />
        </div>

        <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-4">
          <div className="mb-1 text-xs font-medium uppercase tracking-wide text-emerald-700">
            Stablecoin corridor alternative
          </div>
          <Row label="Stablecoin cost" value={money(tx.stablecoinCost)} />
          <Row label="You would save" value={money(tx.stablecoinSavings)} />
        </div>

        <div className="mt-4 space-y-1 text-xs text-slate-500">
          <div>Authorized: {new Date(tx.authorizedAt).toLocaleString()}</div>
          <div>Settled: {tx.settledAt ? new Date(tx.settledAt).toLocaleString() : 'pending'}</div>
          <div className="pt-1 text-slate-400">
            True FX cost is set at settlement (T+1–T+3); the estimate at swipe can differ.
          </div>
        </div>
      </aside>
    </div>
  )
}
