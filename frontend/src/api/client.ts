import type { SimulateRequest, Summary, Transaction } from './types'

async function asJson<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`Request failed: HTTP ${res.status}`)
  }
  return (await res.json()) as T
}

export async function getTransactions(): Promise<Transaction[]> {
  return asJson<Transaction[]>(await fetch('/api/transactions'))
}

export async function getSummary(): Promise<Summary> {
  return asJson<Summary>(await fetch('/api/summary'))
}

export async function simulate(req: SimulateRequest): Promise<Transaction> {
  const res = await fetch('/api/simulate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  return asJson<Transaction>(res)
}

export type StreamHandler = (event: 'tx.created' | 'tx.updated', data: Transaction) => void

export function openStream(onEvent: StreamHandler): () => void {
  if (typeof EventSource === 'undefined') {
    return () => {}
  }
  const es = new EventSource('/api/stream')
  const listen = (name: 'tx.created' | 'tx.updated') =>
    es.addEventListener(name, (e: MessageEvent) => onEvent(name, JSON.parse(e.data) as Transaction))
  listen('tx.created')
  listen('tx.updated')
  return () => es.close()
}
