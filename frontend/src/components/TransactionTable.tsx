import type { Transaction, TransactionStatus } from '../api/types'
import { bps, money } from '../lib/format'

interface Props {
  transactions: Transaction[]
  onSelect: (tx: Transaction) => void
}

const statusStyles: Record<TransactionStatus, string> = {
  AUTHORIZED: 'bg-amber-100 text-amber-800',
  SETTLING: 'bg-sky-100 text-sky-800',
  SETTLED: 'bg-emerald-100 text-emerald-800',
}

export default function TransactionTable({ transactions, onSelect }: Props) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3">Merchant</th>
            <th className="px-4 py-3">Corridor</th>
            <th className="px-4 py-3 text-right">Purchase</th>
            <th className="px-4 py-3 text-right">Settled (USD)</th>
            <th className="px-4 py-3 text-right">Markup</th>
            <th className="px-4 py-3 text-right">Hidden FX</th>
            <th className="px-4 py-3">Status</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {transactions.map((t) => (
            <tr
              key={t.id}
              onClick={() => onSelect(t)}
              className="cursor-pointer hover:bg-slate-50"
            >
              <td className="px-4 py-3 font-medium text-slate-900">{t.merchant}</td>
              <td className="px-4 py-3 text-slate-600">{t.corridor}</td>
              <td className="px-4 py-3 text-right tabular-nums text-slate-600">
                {t.purchaseAmount.toLocaleString()} {t.corridor}
              </td>
              <td className="px-4 py-3 text-right tabular-nums">{money(t.settledCost)}</td>
              <td className="px-4 py-3 text-right tabular-nums text-slate-600">{bps(t.effectiveMarkupBps)}</td>
              <td className="px-4 py-3 text-right font-medium tabular-nums text-rose-600">{money(t.hiddenFxCost)}</td>
              <td className="px-4 py-3">
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyles[t.status]}`}>
                  {t.status}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
