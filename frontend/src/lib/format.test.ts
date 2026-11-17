import { bps, money, pct } from './format'

describe('format', () => {
  it('formats USD money', () => {
    expect(money(55.35)).toBe('$55.35')
  })

  it('renders a dash for null money', () => {
    expect(money(null)).toBe('—')
  })

  it('formats basis points', () => {
    expect(bps(250)).toBe('250 bps')
  })

  it('formats a percentage', () => {
    expect(pct(0.025)).toBe('2.50%')
  })
})
