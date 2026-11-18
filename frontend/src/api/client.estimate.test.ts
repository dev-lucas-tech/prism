import { getEstimate } from './client'

describe('client getEstimate', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('requests the endpoint and parses the body', async () => {
    const body = {
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
    }
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const out = await getEstimate('BRL', 1800)

    expect(fetchMock).toHaveBeenCalledWith('/api/estimate?corridor=BRL&amount=1800')
    expect(out.estimatedHiddenFxCost).toBe(9.99)
    expect(out.effectiveMarkupBps).toBe(300)
  })
})
