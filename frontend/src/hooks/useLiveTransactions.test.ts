import { act, renderHook } from '@testing-library/react'
import type { StreamHandler } from '../api/client'
import type { Transaction } from '../api/types'
import { useLiveTransactions } from './useLiveTransactions'

const h = vi.hoisted(() => ({ captured: null as StreamHandler | null }))

vi.mock('../api/client', () => ({
  openStream: (onEvent: StreamHandler) => {
    h.captured = onEvent
    return () => {}
  },
}))

function tx(overrides: Partial<Transaction>): Transaction {
  return {
    id: 'x',
    employee: 'E',
    merchant: 'M',
    merchantCountry: 'Mexico',
    corridor: 'MXN',
    purchaseAmount: 1000,
    fundingCurrency: 'USD',
    status: 'AUTHORIZED',
    authorizedAt: '2026-11-17T00:00:00Z',
    settledAt: null,
    midAuthRate: 0.054,
    midSettleRate: null,
    estimatedCost: 54,
    midMarketCost: null,
    settledCost: null,
    hiddenFxCost: null,
    effectiveMarkupBps: null,
    stablecoinCost: null,
    stablecoinSavings: null,
    surpriseVsEstimate: null,
    ...overrides,
  }
}

describe('useLiveTransactions', () => {
  it('merges an update into the same transaction by id', () => {
    const { result } = renderHook(() => useLiveTransactions())

    act(() => h.captured!('tx.created', tx({ id: 'live-1', status: 'AUTHORIZED' })))
    act(() =>
      h.captured!('tx.updated', tx({ id: 'live-1', status: 'SETTLED', settledCost: 55.35, hiddenFxCost: 1.35 })),
    )

    expect(result.current.live).toHaveLength(1)
    expect(result.current.live[0].status).toBe('SETTLED')
    expect(result.current.live[0].hiddenFxCost).toBe(1.35)
  })
})
