import { useCallback, useEffect, useRef, useState } from 'react'
import { getSummary, getTransactions } from '../api/client'
import type { Summary, Transaction } from '../api/types'
import { useLiveTransactions } from '../hooks/useLiveTransactions'
import KpiTiles from '../components/KpiTiles'
import CorridorChart from '../components/CorridorChart'
import TransactionTable from '../components/TransactionTable'
import TransactionDrawer from '../components/TransactionDrawer'
import LiveFeed from '../components/LiveFeed'
import SimulateButton from '../components/SimulateButton'
import AnalystPanel from '../components/AnalystPanel'

export default function Dashboard() {
  const [summary, setSummary] = useState<Summary | null>(null)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [selected, setSelected] = useState<Transaction | null>(null)
  const { live } = useLiveTransactions()
  const settledSeen = useRef<Set<string>>(new Set())

  const refresh = useCallback(() => {
    getSummary()
      .then(setSummary)
      .catch(() => {})
    getTransactions()
      .then(setTransactions)
      .catch(() => {})
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  // When a live transaction settles, pull fresh aggregates so FX leakage updates live.
  useEffect(() => {
    const hasNewSettlement = live.some((t) => t.status === 'SETTLED' && !settledSeen.current.has(t.id))
    if (hasNewSettlement) {
      live.forEach((t) => {
        if (t.status === 'SETTLED') settledSeen.current.add(t.id)
      })
      refresh()
    }
  }, [live, refresh])

  if (!summary) {
    return <div className="text-sm text-slate-500">Loading…</div>
  }

  return (
    <div className="space-y-6">
      <KpiTiles summary={summary} />
      <AnalystPanel />
      <div className="grid gap-6 lg:grid-cols-2">
        <CorridorChart breakdown={summary.corridorBreakdown} />
        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-700">Live settlement</h2>
            <SimulateButton />
          </div>
          <LiveFeed live={live} />
        </div>
      </div>
      <div>
        <h2 className="mb-2 text-sm font-semibold text-slate-700">Transactions</h2>
        <TransactionTable transactions={transactions} onSelect={setSelected} />
      </div>
      {selected && <TransactionDrawer tx={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}
