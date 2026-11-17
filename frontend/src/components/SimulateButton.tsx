import { useState } from 'react'
import { simulate } from '../api/client'
import type { Corridor } from '../api/types'

const CORRIDORS: Corridor[] = ['MXN', 'BRL', 'COP', 'EUR', 'GBP']
const MERCHANTS = ['OXXO', 'iFood', 'Rappi', 'Amazon EU', 'Tesco', 'Uber BR']

function randomAmount(c: Corridor): number {
  const base = c === 'COP' ? 500000 : c === 'MXN' ? 2000 : c === 'BRL' ? 500 : 150
  return Math.round(base * (0.5 + Math.random() * 1.5))
}

function pick<T>(xs: T[]): T {
  return xs[Math.floor(Math.random() * xs.length)]
}

export default function SimulateButton() {
  const [busy, setBusy] = useState(false)

  async function onClick() {
    setBusy(true)
    try {
      const corridor = pick(CORRIDORS)
      await simulate({ corridor, amount: randomAmount(corridor), merchant: pick(MERCHANTS) })
    } catch {
      // best-effort in the demo; the feed reflects server state
    } finally {
      setBusy(false)
    }
  }

  return (
    <button
      onClick={onClick}
      disabled={busy}
      className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
    >
      {busy ? 'Simulating…' : 'Simulate a purchase'}
    </button>
  )
}
