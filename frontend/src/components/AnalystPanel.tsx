import { useEffect, useState } from 'react'
import { useAnalyst } from '../hooks/useAnalyst'
import { getAnalystStatus, type Finding, type TraceEntry } from '../api/analyst'
import { money } from '../lib/format'

const SEVERITY_CLASS: Record<string, string> = {
  high: 'bg-rose-100 text-rose-700',
  medium: 'bg-amber-100 text-amber-700',
  low: 'bg-slate-100 text-slate-600',
}

function TraceRow({ entry }: { entry: TraceEntry }) {
  if (entry.kind === 'round') {
    return <div className="text-xs font-medium uppercase tracking-wide text-slate-400">Round {entry.round}</div>
  }
  const arrow = entry.kind === 'tool_call' ? '→' : '✓'
  return (
    <div className="flex items-center gap-2 text-xs text-slate-600">
      <span className="text-slate-400">{arrow}</span>
      <span className="font-mono text-slate-800">{entry.tool}</span>
      <span className="text-slate-400">{entry.kind === 'tool_call' ? 'called' : 'returned'}</span>
    </div>
  )
}

function FindingCard({ finding }: { finding: Finding }) {
  const severity = SEVERITY_CLASS[finding.severity.toLowerCase()] ?? SEVERITY_CLASS.low
  return (
    <div className="rounded-lg border border-slate-200 p-3">
      <div className="mb-1 flex items-center justify-between gap-2">
        <h4 className="text-sm font-semibold text-slate-800">{finding.title}</h4>
        <span className={`rounded px-2 py-0.5 text-xs font-medium ${severity}`}>{finding.severity}</span>
      </div>
      <div className="mb-1 text-sm text-slate-600">
        <span className="font-semibold text-slate-900">{money(Number(finding.dollarsAtStake))}</span> at stake · {finding.corridor}
      </div>
      <p className="mb-2 text-sm text-slate-600">{finding.recommendation}</p>
      {finding.evidenceTxIds.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {finding.evidenceTxIds.map((id) => (
            <span key={id} className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-500">
              {id}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

export default function AnalystPanel() {
  const { status, trace, report, error, run } = useAnalyst()
  const [question, setQuestion] = useState('')
  const [configured, setConfigured] = useState(true)
  const busy = status === 'running'

  useEffect(() => {
    getAnalystStatus()
      .then((s) => setConfigured(s.configured))
      .catch(() => {})
  }, [])

  return (
    <section className="rounded-xl border border-indigo-200 bg-white p-4">
      <div className="mb-3">
        <h2 className="text-sm font-semibold text-indigo-700">AI FX Analyst</h2>
        <p className="text-xs text-slate-500">
          An agent investigates your recent spend over live tools, then reports where you're losing to FX.
        </p>
      </div>

      <div className="mb-3 flex gap-2">
        <input
          className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm"
          placeholder="Ask about your FX spend (optional)…"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          disabled={busy}
        />
        <button
          onClick={() => run(question || undefined)}
          disabled={busy || !configured}
          className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500 disabled:opacity-50"
        >
          {busy ? 'Analyzing…' : 'Analyze recent activity'}
        </button>
      </div>

      {!configured && (
        <p className="mb-2 text-xs text-slate-500">
          Set <code>OPENROUTER_API_KEY</code> in the backend environment to enable the analyst.
        </p>
      )}

      {error && (
        <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">
          {error}
        </p>
      )}

      {trace.length > 0 && (
        <div className="mb-3 space-y-1 rounded-lg bg-slate-50 p-3">
          {trace.map((entry, i) => (
            <TraceRow key={i} entry={entry} />
          ))}
        </div>
      )}

      {report && (
        <div className="space-y-3">
          <div className="text-sm font-semibold text-slate-900">{report.headline}</div>
          {report.findings.map((finding, i) => (
            <FindingCard key={i} finding={finding} />
          ))}
        </div>
      )}
    </section>
  )
}
