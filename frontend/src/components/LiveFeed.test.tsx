import { render, screen } from '@testing-library/react'
import LiveFeed from './LiveFeed'
import type { Transaction } from '../api/types'

function tx(overrides: Partial<Transaction>): Transaction {
  return {
    id: 'x',
    employee: 'E',
    merchant: 'OXXO',
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

describe('LiveFeed', () => {
  it('shows pending for authorized and hidden cost for settled', () => {
    render(
      <LiveFeed
        live={[
          tx({ id: 'a', status: 'AUTHORIZED' }),
          tx({ id: 'b', status: 'SETTLED', settledCost: 55.35, hiddenFxCost: 1.35, effectiveMarkupBps: 250 }),
        ]}
      />,
    )
    expect(screen.getByText(/Pending/i)).toBeInTheDocument()
    expect(screen.getByText(/hidden/i)).toBeInTheDocument()
  })
})
