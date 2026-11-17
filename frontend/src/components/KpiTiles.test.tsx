import { render, screen } from '@testing-library/react'
import KpiTiles from './KpiTiles'
import type { Summary } from '../api/types'

const summary: Summary = {
  totalSpend: 12000,
  totalHiddenFxCost: 325.5,
  effectiveMarkupBps: 275,
  totalStablecoinSavings: 240.25,
  corridorBreakdown: [],
}

describe('KpiTiles', () => {
  it('shows FX leakage and stablecoin savings', () => {
    render(<KpiTiles summary={summary} />)
    expect(screen.getByText('$325.50')).toBeInTheDocument()
    expect(screen.getByText('$240.25')).toBeInTheDocument()
  })
})
