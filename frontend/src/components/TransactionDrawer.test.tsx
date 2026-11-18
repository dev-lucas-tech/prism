import { render, screen } from '@testing-library/react'
import TransactionDrawer from './TransactionDrawer'
import type { Transaction } from '../api/types'

const settledTx: Transaction = {
  id: 't1',
  employee: 'Ana Torres',
  merchant: 'OXXO',
  merchantCountry: 'Mexico',
  corridor: 'MXN',
  purchaseAmount: 1000,
  fundingCurrency: 'USD',
  status: 'SETTLED',
  authorizedAt: '2026-11-10T00:00:00Z',
  settledAt: '2026-11-12T00:00:00Z',
  midAuthRate: 0.054,
  midSettleRate: 0.054,
  estimatedCost: 54,
  midMarketCost: 54,
  settledCost: 55.35,
  hiddenFxCost: 1.35,
  effectiveMarkupBps: 250,
  stablecoinCost: 54.27,
  stablecoinSavings: 1.08,
  surpriseVsEstimate: 1.35,
}

describe('TransactionDrawer', () => {
  it('shows the stablecoin alternative cost and savings', () => {
    render(<TransactionDrawer tx={settledTx} onClose={() => {}} />)
    expect(screen.getByText('$54.27')).toBeInTheDocument()
    expect(screen.getByText('$1.08')).toBeInTheDocument()
  })

  it('renders the two receipt sections with the hidden FX cost', () => {
    render(<TransactionDrawer tx={settledTx} onClose={() => {}} />)
    expect(screen.getByText(/default view/i)).toBeInTheDocument()
    expect(screen.getByText(/showing hidden fees/i)).toBeInTheDocument()
    expect(screen.getByText(/hidden fx cost/i)).toBeInTheDocument()
    expect(screen.getAllByText('$1.35').length).toBeGreaterThan(0)
  })

  it('renders dashes, not NaN, for a not-yet-settled transaction', () => {
    const pending: Transaction = {
      ...settledTx,
      status: 'AUTHORIZED',
      settledAt: null,
      midSettleRate: null,
      settledCost: null,
      midMarketCost: null,
      hiddenFxCost: null,
      effectiveMarkupBps: null,
      stablecoinCost: null,
      stablecoinSavings: null,
      surpriseVsEstimate: null,
    }
    render(<TransactionDrawer tx={pending} onClose={() => {}} />)
    expect(screen.queryByText(/NaN/)).not.toBeInTheDocument()
  })
})
