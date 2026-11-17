export type TransactionStatus = 'AUTHORIZED' | 'SETTLING' | 'SETTLED'

export type Corridor = 'MXN' | 'BRL' | 'COP' | 'EUR' | 'GBP'

export interface Transaction {
  id: string
  employee: string
  merchant: string
  merchantCountry: string
  corridor: Corridor
  purchaseAmount: number
  fundingCurrency: string
  status: TransactionStatus
  authorizedAt: string
  settledAt: string | null
  midAuthRate: number
  midSettleRate: number | null
  estimatedCost: number
  midMarketCost: number | null
  settledCost: number | null
  hiddenFxCost: number | null
  effectiveMarkupBps: number | null
  stablecoinCost: number | null
  stablecoinSavings: number | null
  surpriseVsEstimate: number | null
}

export interface CorridorBreakdown {
  corridor: Corridor
  count: number
  spend: number
  hiddenFxCost: number
  stablecoinSavings: number
}

export interface Summary {
  totalSpend: number
  totalHiddenFxCost: number
  effectiveMarkupBps: number
  totalStablecoinSavings: number
  corridorBreakdown: CorridorBreakdown[]
}

export interface SimulateRequest {
  corridor: Corridor
  amount: number
  merchant: string
}
