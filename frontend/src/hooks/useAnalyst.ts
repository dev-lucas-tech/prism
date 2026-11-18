import { useCallback, useRef, useState } from 'react'
import { openAnalystStream, type AnalystReport, type TraceEntry } from '../api/analyst'

export type AnalystStatus = 'idle' | 'running' | 'done' | 'error'

export interface AnalystState {
  status: AnalystStatus
  trace: TraceEntry[]
  report: AnalystReport | null
  error: string | null
  run: (question?: string) => void
  reset: () => void
}

export function useAnalyst(): AnalystState {
  const [status, setStatus] = useState<AnalystStatus>('idle')
  const [trace, setTrace] = useState<TraceEntry[]>([])
  const [report, setReport] = useState<AnalystReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const closeRef = useRef<(() => void) | null>(null)

  const run = useCallback((question?: string) => {
    closeRef.current?.()
    setStatus('running')
    setTrace([])
    setReport(null)
    setError(null)
    closeRef.current = openAnalystStream(question, {
      onTrace: (entry) => setTrace((prev) => [...prev, entry]),
      onFinal: (r) => {
        setReport(r)
        setStatus('done')
      },
      onError: (message) => {
        setError(message)
        setStatus('error')
      },
    })
  }, [])

  const reset = useCallback(() => {
    closeRef.current?.()
    closeRef.current = null
    setStatus('idle')
    setTrace([])
    setReport(null)
    setError(null)
  }, [])

  return { status, trace, report, error, run, reset }
}
