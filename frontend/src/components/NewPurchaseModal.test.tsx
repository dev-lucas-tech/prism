import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import NewPurchaseModal from './NewPurchaseModal'
import { getEstimate, simulate } from '../api/client'

vi.mock('../api/client', () => ({
  getEstimate: vi.fn().mockResolvedValue({
    corridor: 'BRL',
    country: 'Brazil',
    merchantAmount: 1800,
    midRate: 0.185,
    midMarketCost: 333,
    estimatedCost: 342.99,
    estimatedHiddenFxCost: 9.99,
    effectiveMarkupBps: 300,
    stablecoinFeeBps: 50,
    stablecoinCost: 334.66,
    stablecoinSavings: 8.33,
  }),
  simulate: vi.fn().mockResolvedValue({}),
}))

describe('NewPurchaseModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows both receipt sections and authorizes the purchase', async () => {
    const onClose = vi.fn()
    render(<NewPurchaseModal onClose={onClose} />)

    fireEvent.change(screen.getByLabelText(/merchant amount/i), { target: { value: '1800' } })

    expect(await screen.findByText(/default view/i)).toBeInTheDocument()
    expect(screen.getByText(/showing hidden fees/i)).toBeInTheDocument()
    expect(await screen.findByText(/9\.99/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /authorize/i }))

    await waitFor(() =>
      expect(simulate).toHaveBeenCalledWith(expect.objectContaining({ corridor: 'BRL', amount: 1800 })),
    )
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('does not fetch an estimate and disables Authorize when amount is empty', async () => {
    render(<NewPurchaseModal onClose={vi.fn()} />)
    await new Promise((r) => setTimeout(r, 350))
    expect(getEstimate).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /authorize/i })).toBeDisabled()
  })
})
