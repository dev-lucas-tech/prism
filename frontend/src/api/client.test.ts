import { getSummary } from './client'

describe('client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getSummary parses JSON into a typed summary', async () => {
    const payload = {
      totalSpend: 100,
      totalHiddenFxCost: 5,
      effectiveMarkupBps: 250,
      totalStablecoinSavings: 4,
      corridorBreakdown: [],
    }
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify(payload), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    const s = await getSummary()

    expect(s.totalHiddenFxCost).toBe(5)
    expect(s.effectiveMarkupBps).toBe(250)
  })
})
