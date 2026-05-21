import type { PlanStatus, Direction } from './plan'

export interface DashboardKpis {
  planReturnPercent: number
  actualReturnPercent: number
  holdingGap: number
  planCashBalance: number
  actualCashBalance: number
  activePlanCount: number
  holdingPlanCount: number
}

export interface TrendPoint {
  date: string
  planReturnPercent: number
  actualReturnPercent: number
  planTotalValue: number
  actualTotalValue: number
}

export interface PlanHoldingItem {
  planId: number
  planName: string
  stockCode: string
  status: PlanStatus
  unrealizedPLAmount: number
  unrealizedPLPercent: number
  holdDays: number
}

export interface ActualHoldingItem {
  stockCode: string
  stockName: string
  unrealizedPLAmount: number
  unrealizedPLPercent: number
}

export interface ExecutionLogItem {
  date: string
  type: 'PLAN' | 'ACTUAL'
  content: string
  planStatus?: PlanStatus
  direction?: Direction
}

export interface DashboardResponse {
  baselineCapital: number
  kpis: DashboardKpis
  trend: TrendPoint[]
  planHoldings: PlanHoldingItem[]
  actualHoldings: ActualHoldingItem[]
  executionLog: ExecutionLogItem[]
}
