import { openAnalystStream, type AnalystReport, type TraceEntry } from './analyst'

class MockEventSource {
  static last: MockEventSource | null = null
  url: string
  closed = false
  private listeners: Record<string, ((e: MessageEvent) => void)[]> = {}

  constructor(url: string) {
    this.url = url
    MockEventSource.last = this
  }

  addEventListener(name: string, cb: (e: MessageEvent) => void) {
    ;(this.listeners[name] ||= []).push(cb)
  }

  close() {
    this.closed = true
  }

  emit(name: string, data?: string) {
    ;(this.listeners[name] || []).forEach((cb) => cb({ data } as MessageEvent))
  }
}

beforeEach(() => {
  ;(globalThis as unknown as { EventSource: unknown }).EventSource = MockEventSource
})

describe('openAnalystStream', () => {
  it('parses round, tool_call and final events and closes on final', () => {
    const trace: TraceEntry[] = []
    let report: AnalystReport | null = null
    openAnalystStream(undefined, { onTrace: (e) => trace.push(e), onFinal: (r) => (report = r), onError: () => {} })

    const es = MockEventSource.last!
    es.emit('round_start', JSON.stringify({ round: 1 }))
    es.emit('tool_call', JSON.stringify({ tool: 'get_spend_summary', arguments: {} }))
    es.emit('final', JSON.stringify({ headline: 'H', totalAddressable: '42.00', findings: [] }))

    expect(trace[0]).toEqual({ kind: 'round', round: 1 })
    expect(trace[1]).toMatchObject({ kind: 'tool_call', tool: 'get_spend_summary' })
    expect(report).not.toBeNull()
    expect(report!.headline).toBe('H')
    expect(es.closed).toBe(true)
  })

  it('passes the question as a query param', () => {
    openAnalystStream('why so costly', { onTrace: () => {}, onFinal: () => {}, onError: () => {} })
    expect(MockEventSource.last!.url).toContain('q=why')
  })

  it('surfaces a server error event message', () => {
    let message = ''
    openAnalystStream(undefined, { onTrace: () => {}, onFinal: () => {}, onError: (m) => (message = m) })
    MockEventSource.last!.emit('error', JSON.stringify({ message: 'AI is not configured yet' }))
    expect(message).toContain('not configured')
  })
})
