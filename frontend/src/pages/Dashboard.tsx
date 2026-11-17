import { useEffect, useState } from 'react'
import { getSummary, getTransactions } from '../api/client'
import type { Summary, Transaction } from '../api/types'
import KpiTiles from '../components/KpiTiles'
import CorridorChart from '../components/CorridorChart'
import TransactionTable from '../components/TransactionTable'
import TransactionDrawer from '../components/TransactionDrawer'

export default function Dashboard() {
  const [summary, setSummary] = useState<Summary | null>(null)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [selected, setSelected] = useState<Transaction | null>(null)

  useEffect(() => {
    let active = true
    getSummary()
      .then((s) => active && setSummary(s))
      .catch(() => active && setSummary(null))
    getTransactions()
      .then((t) => active && setTransactions(t))
      .catch(() => active && setTransactions([]))
    return () => {
      active = false
    }
  }, [])

  if (!summary) {
    return <div className="text-sm text-slate-500">Loading…</div>
  }

  return (
    <div className="space-y-6">
      <KpiTiles summary={summary} />
      <CorridorChart breakdown={summary.corridorBreakdown} />
      <div>
        <h2 className="mb-2 text-sm font-semibold text-slate-700">Transactions</h2>
        <TransactionTable transactions={transactions} onSelect={setSelected} />
      </div>
      {selected && <TransactionDrawer tx={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}
