export interface Finding {
  title: string
  severity: string
  dollarsAtStake: string | number
  corridor: string
  evidenceTxIds: string[]
  recommendation: string
}

export interface AnalystReport {
  headline: string
  totalAddressable: string | number
  findings: Finding[]
}

export async function getAnalystStatus(): Promise<{ configured: boolean }> {
  try {
    const res = await fetch('/api/analyst/status')
    if (!res.ok) return { configured: true }
    return (await res.json()) as { configured: boolean }
  } catch {
    return { configured: true }
  }
}

export type TraceEntry =
  | { kind: 'round'; round: number }
  | { kind: 'tool_call'; tool: string; arguments: unknown }
  | { kind: 'tool_result'; tool: string; result: unknown }

export interface AnalystHandlers {
  onTrace: (entry: TraceEntry) => void
  onFinal: (report: AnalystReport) => void
  onError: (message: string) => void
}

export function openAnalystStream(question: string | undefined, handlers: AnalystHandlers): () => void {
  if (typeof EventSource === 'undefined') {
    handlers.onError('Live streaming is not supported in this browser.')
    return () => {}
  }

  const q = question && question.trim()
  const url = q ? `/api/analyst/stream?q=${encodeURIComponent(q)}` : '/api/analyst/stream'
  const es = new EventSource(url)
  const close = () => es.close()

  es.addEventListener('round_start', (e: MessageEvent) =>
    handlers.onTrace({ kind: 'round', round: JSON.parse(e.data).round }),
  )
  es.addEventListener('tool_call', (e: MessageEvent) => {
    const d = JSON.parse(e.data)
    handlers.onTrace({ kind: 'tool_call', tool: d.tool, arguments: d.arguments })
  })
  es.addEventListener('tool_result', (e: MessageEvent) => {
    const d = JSON.parse(e.data)
    handlers.onTrace({ kind: 'tool_result', tool: d.tool, result: d.result })
  })
  es.addEventListener('final', (e: MessageEvent) => {
    handlers.onFinal(JSON.parse(e.data) as AnalystReport)
    close()
  })
  es.addEventListener('error', (e: MessageEvent) => {
    const message = e.data ? JSON.parse(e.data).message : 'The analyst connection was interrupted.'
    handlers.onError(message)
    close()
  })

  return close
}
