import { useEffect, useState } from 'react'
import { getEstimate, simulate } from '../api/client'
import type { Corridor, Estimate } from '../api/types'
import { bps, money, rate } from '../lib/format'

const CORRIDORS: Corridor[] = ['MXN', 'BRL', 'COP', 'EUR', 'GBP']

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

export default function NewPurchaseModal({ onClose }: { onClose: () => void }) {
  const [corridor, setCorridor] = useState<Corridor>('BRL')
  const [amountText, setAmountText] = useState('')
  const [merchant, setMerchant] = useState('')
  const [estimate, setEstimate] = useState<Estimate | null>(null)
  const [busy, setBusy] = useState(false)

  const amount = Number(amountText)
  const validAmount = amountText.trim() !== '' && Number.isFinite(amount) && amount > 0

  useEffect(() => {
    if (!validAmount) {
      setEstimate(null)
      return
    }
    let cancelled = false
    const handle = setTimeout(() => {
      getEstimate(corridor, amount)
        .then((e) => {
          if (!cancelled) setEstimate(e)
        })
        .catch(() => {
          if (!cancelled) setEstimate(null)
        })
    }, 300)
    return () => {
      cancelled = true
      clearTimeout(handle)
    }
  }, [corridor, amount, validAmount])

  async function onAuthorize() {
    if (!validAmount) return
    setBusy(true)
    try {
      await simulate({ corridor, amount, merchant: merchant.trim() || 'New purchase' })
      onClose()
    } catch {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-slate-900/40" onClick={onClose} aria-hidden="true" />
      <div className="relative z-30 max-h-[90vh] w-full max-w-md overflow-y-auto rounded-xl bg-white p-6 shadow-xl">
        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">New card purchase</h2>
            <p className="text-sm text-slate-500">See the true FX cost before it settles.</p>
          </div>
          <button onClick={onClose} aria-label="Close" className="rounded p-1 text-slate-400 hover:bg-slate-100">
            ✕
          </button>
        </div>

        <div className="mt-4 space-y-3">
          <label className="block text-sm">
            <span className="mb-1 block text-slate-600">Destination</span>
            <select
              value={corridor}
              onChange={(e) => setCorridor(e.target.value as Corridor)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2"
            >
              {CORRIDORS.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-slate-600">Merchant amount ({corridor})</span>
            <input
              aria-label="Merchant amount"
              inputMode="decimal"
              value={amountText}
              onChange={(e) => setAmountText(e.target.value)}
              placeholder="0.00"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 tabular-nums"
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-slate-600">Merchant</span>
            <input
              aria-label="Merchant"
              value={merchant}
              onChange={(e) => setMerchant(e.target.value)}
              placeholder="e.g. iFood"
              className="w-full rounded-lg border border-slate-300 px-3 py-2"
            />
          </label>
        </div>

        {estimate && (
          <div className="mt-4 space-y-3">
            <section className="rounded-lg border border-slate-200 p-4">
              <div className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-500">
                What you see in Jeeves today
              </div>
              <Row
                label="Merchant charge"
                value={`${estimate.merchantAmount.toLocaleString()} ${estimate.corridor}`}
              />
              <Row label="Amount charged" value={money(estimate.estimatedCost)} />
              <Row
                label="Conversion rate"
                value={`${rate(estimate.estimatedCost / estimate.merchantAmount)} / ${estimate.corridor}`}
              />
            </section>
            <section className="rounded-lg border border-indigo-200 bg-indigo-50 p-4">
              <div className="mb-1 text-xs font-medium uppercase tracking-wide text-indigo-700">
                What Prism adds
              </div>
              <Row label="Mid-market reference" value={money(estimate.midMarketCost)} />
              <Row label="Est. hidden FX cost" value={money(estimate.estimatedHiddenFxCost)} accent />
              <Row label="Effective markup" value={bps(estimate.effectiveMarkupBps)} />
              <Row label="Via stablecoin corridor" value={money(estimate.stablecoinCost)} />
              <Row label="You'd save" value={money(estimate.stablecoinSavings)} />
              <p className="mt-2 text-xs text-slate-500">
                This is an estimate. The true FX cost is set at settlement (T+1–T+3) and can differ.
              </p>
            </section>
          </div>
        )}

        <div className="mt-5 flex justify-end gap-2">
          <button
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Cancel
          </button>
          <button
            onClick={onAuthorize}
            disabled={!validAmount || busy}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
          >
            {busy ? 'Authorizing…' : 'Authorize purchase'}
          </button>
        </div>
      </div>
    </div>
  )
}
