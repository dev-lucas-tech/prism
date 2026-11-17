import { render, screen } from '@testing-library/react'
import App from './App'

describe('App', () => {
  it('renders the Prism header', () => {
    render(<App />)
    expect(screen.getByText(/Prism/i)).toBeInTheDocument()
  })
})
