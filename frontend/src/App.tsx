import Dashboard from './pages/Dashboard'

function App() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-5">
          <h1 className="text-xl font-semibold tracking-tight">Prism — FX Transparency</h1>
          <p className="text-sm text-slate-500">
            The real cost of every corporate-card transaction, revealed at settlement.
          </p>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Dashboard />
      </main>
    </div>
  )
}

export default App
