import { useEffect, useState } from 'react'
import { openStream } from '../api/client'
import type { Transaction } from '../api/types'

/**
 * Subscribes to the SSE stream and keeps a list of live transactions, applying
 * tx.created (prepend) and tx.updated (merge by id) so a row flips in place
 * from "pending, estimated" to "settled, true cost".
 */
export function useLiveTransactions(): { live: Transaction[] } {
  const [live, setLive] = useState<Transaction[]>([])

  useEffect(() => {
    const close = openStream((event, tx) => {
      setLive((prev) => {
        if (event === 'tx.created') {
          return [tx, ...prev.filter((t) => t.id !== tx.id)]
        }
        const idx = prev.findIndex((t) => t.id === tx.id)
        if (idx === -1) {
          return [tx, ...prev]
        }
        const next = prev.slice()
        next[idx] = tx
        return next
      })
    })
    return close
  }, [])

  return { live }
}
