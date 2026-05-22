import type {
  Plan,
} from '@/types'
import type { SystemConfig, PlanAccount, ActualAccount } from '@/types'

// 延迟模拟网络请求
const delay = (ms: number) => new Promise((r) => setTimeout(r, ms))

// 基准资金
const mockSystemConfig: SystemConfig = {
  id: 1,
  baselineCapital: 500000,
  updatedAt: '2026-05-21T10:00:00',
}

const mockPlanAccount: PlanAccount = {
  id: 1,
  cashBalance: 483200,
  updatedAt: '2026-05-21T15:00:00',
}

const mockActualAccount: ActualAccount = {
  id: 1,
  cashBalance: 495000,
  updatedAt: '2026-05-21T15:00:00',
}

// 预案列表 - 买入卖出分离模型
const mockPlans: Plan[] = [
  {
    id: 1,
    name: '茅台 MA20 买入',
    stockCode: '600519',
    stockName: '贵州茅台',
    cycle: 'WEEKLY',
    planType: 'BUY',
    status: 'HOLDING',
    tradePlanId: 1,
    validUntil: '2026-05-31',
    triggerDate: '2026-05-15',
    executionQuantity: 100,
    createdAt: '2026-05-01T10:23:00',
    updatedAt: '2026-05-15T15:00:00',
    condition: { id: 1, planId: 1, conditionType: 'MA', maPeriod: 20 },
  },
  {
    id: 2,
    name: '茅台 MA10 卖出',
    stockCode: '600519',
    stockName: '贵州茅台',
    cycle: 'WEEKLY',
    planType: 'SELL',
    status: 'PENDING',
    tradePlanId: 1,
    buyPlanId: 1,
    validUntil: '2026-06-15',
    executionQuantity: 100,
    createdAt: '2026-05-15T15:00:00',
    updatedAt: '2026-05-15T15:00:00',
    condition: { id: 2, planId: 2, conditionType: 'MA', maPeriod: 10 },
  },
  {
    id: 3,
    name: '平安跌破买入',
    stockCode: '601318',
    stockName: '中国平安',
    cycle: 'WEEKLY',
    planType: 'BUY',
    status: 'PENDING',
    tradePlanId: 3,
    validUntil: '2026-05-25',
    executionQuantity: 200,
    createdAt: '2026-04-20T09:00:00',
    updatedAt: '2026-04-20T09:00:00',
    condition: { id: 3, planId: 3, conditionType: 'PRICE', targetPrice: 42.5 },
  },
  {
    id: 4,
    name: '格力周度买入',
    stockCode: '000651',
    stockName: '格力电器',
    cycle: 'WEEKLY',
    planType: 'BUY',
    status: 'EXPIRED',
    tradePlanId: 4,
    validUntil: '2026-03-28',
    executionQuantity: 300,
    createdAt: '2026-03-20T11:00:00',
    updatedAt: '2026-03-28T17:00:00',
    condition: { id: 4, planId: 4, conditionType: 'PRICE', targetPrice: 38.0 },
  },
]

// 预案列表（简洁版，用于列表页）
export const mockPlanListItems = mockPlans.map((p) => ({
  id: p.id,
  name: p.name,
  stockCode: p.stockCode,
  stockName: p.stockName,
  cycle: p.cycle,
  planType: p.planType,
  status: p.status,
  tradePlanId: p.tradePlanId,
  buyPlanId: p.buyPlanId,
  validUntil: p.validUntil,
  triggerDate: p.triggerDate,
  createdAt: p.createdAt,
}))

// 执行记录
export const mockExecutions = [
  { id: 1, planId: 1, tradeDate: '2026-05-15', triggered: true, triggerPrice: 1800, closePrice: 1820, executed: true, createdAt: '2026-05-15T15:00:00' },
  { id: 2, planId: 2, tradeDate: '2026-05-20', triggered: true, triggerPrice: 1850, closePrice: 1845, executed: true, linkedExecutionId: 1, createdAt: '2026-05-20T15:00:00' },
]

// ========== Mock API 实现 ==========

export const mockApi = {
  getSystemConfig: async () => {
    await delay(100)
    return mockSystemConfig
  },

  getPlanAccount: async () => {
    await delay(100)
    return mockPlanAccount
  },

  getActualAccount: async () => {
    await delay(100)
    return mockActualAccount
  },

  listPlans: async (filters?: { status?: string; stockCode?: string }) => {
    await delay(200)
    let plans = mockPlans
    if (filters?.status) {
      plans = plans.filter((p) => p.status === filters.status)
    }
    if (filters?.stockCode) {
      plans = plans.filter((p) => p.stockCode.includes(filters.stockCode!))
    }
    return plans
  },

  getPlan: async (id: number) => {
    await delay(100)
    const plan = mockPlans.find((p) => p.id === id)
    if (!plan) throw new Error('预案不存在')
    return plan
  },

  listExecutions: async (planId: number) => {
    await delay(100)
    return mockExecutions.filter((e) => e.planId === planId)
  },
}
