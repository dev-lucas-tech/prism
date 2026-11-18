import { render, screen } from '@testing-library/react'
import type { AnalystState } from '../hooks/useAnalyst'
import AnalystPanel from './AnalystPanel'

const h = vi.hoisted(() => ({ state: null as AnalystState | null, configured: true }))

vi.mock('../hooks/useAnalyst', () => ({
  useAnalyst: () => h.state,
}))

vi.mock('../api/analyst', () => ({
  getAnalystStatus: () => Promise.resolve({ configured: h.configured }),
}))

beforeEach(() => {
  h.configured = true
})

function baseState(overrides: Partial<AnalystState>): AnalystState {
  return {
    status: 'idle',
    trace: [],
    report: null,
    error: null,
    run: () => {},
    reset: () => {},
    ...overrides,
  }
}

describe('AnalystPanel', () => {
  it('renders the live tool-trace then the final findings cards', () => {
    h.state = baseState({
      status: 'done',
      trace: [{ kind: 'tool_call', tool: 'get_spend_summary', arguments: {} }],
      report: {
        headline: 'You lost $42 to FX',
        totalAddressable: '42.00',
        findings: [
          {
            title: 'BRL markup',
            severity: 'high',
            dollarsAtStake: '24.00',
            corridor: 'BRL',
            evidenceTxIds: ['t1'],
            recommendation: 'route via stablecoin',
          },
        ],
      },
    })

    render(<AnalystPanel />)

    expect(screen.getByText('get_spend_summary')).toBeInTheDocument()
    expect(screen.getByText('You lost $42 to FX')).toBeInTheDocument()
    expect(screen.getByText('BRL markup')).toBeInTheDocument()
    expect(screen.getByText('$24.00')).toBeInTheDocument()
    expect(screen.getByText('t1')).toBeInTheDocument()
  })

  it('formats a numeric dollarsAtStake (as the backend serializes BigDecimal)', () => {
    h.state = baseState({
      status: 'done',
      report: {
        headline: 'h',
        totalAddressable: 42,
        findings: [
          {
            title: 'MXN markup',
            severity: 'medium',
            dollarsAtStake: 24,
            corridor: 'MXN',
            evidenceTxIds: [],
            recommendation: 'pay local',
          },
        ],
      },
    })

    render(<AnalystPanel />)

    expect(screen.getByText('$24.00')).toBeInTheDocument()
  })

  it('disables the button and hints when the analyst is not configured', async () => {
    h.state = baseState({ status: 'idle' })
    h.configured = false

    render(<AnalystPanel />)

    expect(await screen.findByText(/OPENROUTER_API_KEY/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /analyze recent activity/i })).toBeDisabled()
  })

  it('shows a friendly error state', () => {
    h.state = baseState({
      status: 'error',
      error: 'AI is not configured. Set OPENROUTER_API_KEY to enable the analyst.',
    })

    render(<AnalystPanel />)

    expect(screen.getByRole('alert')).toHaveTextContent('not configured')
  })
})
