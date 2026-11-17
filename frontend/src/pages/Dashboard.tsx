import { useEffect, useState } from 'react'
import { getSummary } from '../api/client'
import type { Summary } from '../api/types'
import KpiTiles from '../components/KpiTiles'
import CorridorChart from '../components/CorridorChart'

export default function Dashboard() {
  const [summary, setSummary] = useState<Summary | null>(null)

  useEffect(() => {
    let active = true
    getSummary()
      .then((s) => {
        if (active) setSummary(s)
      })
      .catch(() => {
        if (active) setSummary(null)
      })
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
    </div>
  )
}
