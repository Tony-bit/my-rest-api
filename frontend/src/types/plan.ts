export type Period = 'WEEK' | 'MONTH' | 'YEAR'
export type PlanStatus = 'PENDING' | 'HOLDING' | 'CLOSED' | 'EXPIRED'
export type Cycle = 'DAILY' | 'WEEKLY' | 'MONTHLY'
export type ConditionType = 'PRICE' | 'MA'
export type PlanType = 'BUY' | 'SELL'
export type Direction = 'BUY' | 'SELL'

export interface PlanCondition {
  id: number
  planId: number
  conditionType: ConditionType
  maPeriod?: number
  targetPrice?: number
}

export interface Plan {
  id: number
  name: string
  stockCode: string
  stockName: string
  cycle: Cycle
  planType: PlanType
  status: PlanStatus
  tradePlanId?: number
  buyPlanId?: number
  validUntil: string
  triggerDate?: string
  executionQuantity: number
  condition?: PlanCondition
  createdAt: string
  updatedAt: string
}

export interface PlanListItem {
  id: number
  name: string
  stockCode: string
  stockName: string
  cycle: Cycle
  planType: PlanType
  status: PlanStatus
  tradePlanId?: number
  buyPlanId?: number
  validUntil: string
  triggerDate?: string
  createdAt: string
}
