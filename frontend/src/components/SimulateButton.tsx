import { useState } from 'react'
import NewPurchaseModal from './NewPurchaseModal'

export default function SimulateButton() {
  const [open, setOpen] = useState(false)

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
      >
        Simulate a purchase
      </button>
      {open && <NewPurchaseModal onClose={() => setOpen(false)} />}
    </>
  )
}
