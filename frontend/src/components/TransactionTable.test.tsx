import { render, screen } from '@testing-library/react'
import TransactionTable from './TransactionTable'
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

describe('TransactionTable', () => {
  it('renders the hidden FX value for a settled row', () => {
    render(<TransactionTable transactions={[settledTx]} onSelect={() => {}} />)
    expect(screen.getByText('OXXO')).toBeInTheDocument()
    expect(screen.getByText('$1.35')).toBeInTheDocument()
  })
})
