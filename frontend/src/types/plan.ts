export type Period = 'WEEK' | 'MONTH' | 'YEAR'
export type PlanStatus = 'PENDING' | 'HOLDING' | 'CLOSED' | 'EXPIRED'
export type Cycle = 'DAILY' | 'WEEKLY' | 'MONTHLY'
export type ConditionType = 'PRICE' | 'MA'
export type Direction = 'BUY' | 'SELL'

export interface PlanCondition {
  id: number
  planId: number
  conditionType: ConditionType
  direction: Direction
  maPeriod?: number
  targetPrice?: number
  isActive?: boolean
}

export interface Plan {
  id: number
  name: string
  stockCode: string
  stockName: string
  cycle: Cycle
  status: PlanStatus
  isLocked: boolean
  validUntil: string
  triggerDate?: string
  executionQuantity: number
  createdAt: string
  updatedAt: string
  conditions: PlanCondition[]
}

export interface PlanListItem {
  id: number
  name: string
  stockCode: string
  stockName: string
  cycle: Cycle
  status: PlanStatus
  isLocked: boolean
  validUntil: string
  triggerDate?: string
  createdAt: string
}
