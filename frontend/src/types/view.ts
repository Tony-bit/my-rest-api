export type Period = 'WEEK' | 'MONTH' | 'YEAR'

export interface PlanHolding {
  planId: number
  planName: string
  stockCode: string
  stockName: string
  status: string
  quantity: number
  costPrice: number
  currentPrice: number
  unrealizedPLAmount: number
  unrealizedPLPercent: number
  holdDays: number
  highPrice: number
  lowPrice: number
  closePrice: number
  entryDate: string
}

export interface ActualHolding {
  stockCode: string
  stockName: string
  quantity: number
  avgCostPrice: number
  currentPrice: number
  unrealizedPLAmount: number
  unrealizedPLPercent: number
}

export interface HoldingsSummary {
  totalPlanUnrealizedPL: number
  totalPlanUnrealizedPLPercent: number
  totalActualUnrealizedPL: number
  totalActualUnrealizedPLPercent: number
  holdingGap: number
  planTotalValue: number
  actualTotalValue: number
  planReturnPercent: number
  actualReturnPercent: number
}

export interface HoldingsResponse {
  baselineCapital: number
  planCashBalance: number
  actualCashBalance: number
  planHoldings: PlanHolding[]
  actualHoldings: ActualHolding[]
  summary: HoldingsSummary
}

export interface PlanSummary {
  totalTrades: number
  pendingCount: number
  totalReturn: number
  avgReturn: number
}

export interface ActualSummary {
  totalTrades: number
  totalReturn: number
  avgReturn: number
}

export interface PeriodSummary {
  period: Period
  planSummary: PlanSummary
  actualSummary: ActualSummary
  gapPercent: number
  planList: PlanPeriodItem[]
  actualList: ActualPeriodItem[]
}

export interface PlanPeriodItem {
  planId: number
  planName: string
  stockCode: string
  status: string
  returnPercent: number
}

export interface ActualPeriodItem {
  tradeId: number
  stockCode: string
  tradeDate: string
  returnPercent: number
}

export interface Snapshot {
  id: number
  planId: number
  actualTradeId?: number | null
  snapshotDate: string
  stockCode: string
  stockName: string
  planStatus: string
  planReturn: number
  planReturnPercent: number
  hasActualTrade: boolean
  actualReturn?: number | null
  actualReturnPercent?: number | null
  openQuantity: number
  avgCostPrice: number
  closePrice: number
  highPrice: number
  lowPrice: number
  planCashBalance: number
  planMarketValue: number
  planTotalValue: number
  planReturnPct: number
  actualCashBalance: number
  actualMarketValue: number
  actualTotalValue: number
  actualReturnPct: number
  createdAt: string
}
