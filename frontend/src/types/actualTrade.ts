export type Direction = 'BUY' | 'SELL'

export interface ActualTrade {
  id: number
  stockCode: string
  stockName: string
  direction: Direction
  price: number
  quantity: number
  tradeDate: string
  profitLossAmount?: number
  profitLossPercent?: number
  matched?: boolean
  matchedBuyId?: number | null
  createdAt: string
  updatedAt?: string
}
