import { act, renderHook } from '@testing-library/react'
import type { AnalystHandlers } from '../api/analyst'
import { useAnalyst } from './useAnalyst'

const h = vi.hoisted(() => ({ handlers: null as AnalystHandlers | null, closed: false }))

vi.mock('../api/analyst', () => ({
  openAnalystStream: (_question: string | undefined, handlers: AnalystHandlers) => {
    h.handlers = handlers
    return () => {
      h.closed = true
    }
  },
}))

describe('useAnalyst', () => {
  it('accumulates trace then stores the final report', () => {
    const { result } = renderHook(() => useAnalyst())

    act(() => result.current.run())
    expect(result.current.status).toBe('running')

    act(() => h.handlers!.onTrace({ kind: 'round', round: 1 }))
    act(() => h.handlers!.onFinal({ headline: 'H', totalAddressable: '42.00', findings: [] }))

    expect(result.current.status).toBe('done')
    expect(result.current.trace).toHaveLength(1)
    expect(result.current.report!.headline).toBe('H')
  })

  it('captures an error and moves to the error state', () => {
    const { result } = renderHook(() => useAnalyst())

    act(() => result.current.run())
    act(() => h.handlers!.onError('AI is not configured'))

    expect(result.current.status).toBe('error')
    expect(result.current.error).toContain('not configured')
  })
})
