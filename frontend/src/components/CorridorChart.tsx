import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { CorridorBreakdown } from '../api/types'
import { money } from '../lib/format'

interface Props {
  breakdown: CorridorBreakdown[]
}

export default function CorridorChart({ breakdown }: Props) {
  const data = breakdown.map((b) => ({ corridor: b.corridor, hiddenFxCost: b.hiddenFxCost }))
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="mb-3 text-sm font-semibold text-slate-700">Hidden FX cost by corridor</h2>
      <div style={{ width: '100%', height: 260 }}>
        <ResponsiveContainer>
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
            <XAxis dataKey="corridor" tick={{ fontSize: 12 }} />
            <YAxis tick={{ fontSize: 12 }} width={48} />
            <Tooltip formatter={(value) => money(Number(value))} />
            <Bar dataKey="hiddenFxCost" name="Hidden FX" fill="#e11d48" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
