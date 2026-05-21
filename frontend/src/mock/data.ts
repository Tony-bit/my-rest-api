import type {
  Plan,
  PlanExecution,
  ActualTrade,
  HoldingsResponse,
  PeriodSummary,
  Snapshot,
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

// 预案列表
const mockPlans: Plan[] = [
  {
    id: 1,
    name: '茅台 MA20 买入',
    stockCode: '600519',
    stockName: '贵州茅台',
    cycle: 'WEEKLY',
    status: 'HOLDING',
    validUntil: '2026-05-31',
    shares: 100,
    createdAt: '2026-05-01T10:23:00',
    updatedAt: '2026-05-15T15:00:00',
    conditions: [
      { id: 1, planId: 1, conditionType: 'MA', direction: 'BUY', maPeriod: 20 },
      { id: 2, planId: 1, conditionType: 'MA', direction: 'SELL', maPeriod: 10 },
    ],
  },
  {
    id: 2,
    name: '平安跌破买入',
    stockCode: '601318',
    stockName: '中国平安',
    cycle: 'WEEKLY',
    status: 'PENDING',
    validUntil: '2026-05-25',
    shares: 200,
    createdAt: '2026-04-20T09:00:00',
    updatedAt: '2026-04-20T09:00:00',
    conditions: [
      { id: 3, planId: 2, conditionType: 'PRICE', direction: 'BUY', targetPrice: 42.5 },
    ],
  },
  {
    id: 3,
    name: '五粮液 MA5 卖出',
    stockCode: '000858',
    stockName: '五粮液',
    cycle: 'MONTHLY',
    status: 'CLOSED',
    validUntil: '2026-04-30',
    shares: 1000,
    createdAt: '2026-04-01T08:00:00',
    updatedAt: '2026-04-15T15:00:00',
    conditions: [
      { id: 4, planId: 3, conditionType: 'MA', direction: 'BUY', maPeriod: 5 },
      { id: 5, planId: 3, conditionType: 'MA', direction: 'SELL', maPeriod: 10 },
    ],
  },
  {
    id: 4,
    name: '格力周度买入',
    stockCode: '000651',
    stockName: '格力电器',
    cycle: 'WEEKLY',
    status: 'EXPIRED',
    validUntil: '2026-03-28',
    shares: 300,
    createdAt: '2026-03-20T11:00:00',
    updatedAt: '2026-03-28T17:00:00',
    conditions: [
      { id: 6, planId: 4, conditionType: 'PRICE', direction: 'BUY', targetPrice: 38.0 },
    ],
  },
]

// 预案执行记录
const mockExecutions: Record<number, PlanExecution[]> = {
  1: [
    {
      id: 1,
      planId: 1,
      tradeDate: '2026-05-15',
      direction: 'BUY',
      triggerPrice: 1850.0,
      maValue: 1847.5,
      quantity: 100,
    },
  ],
  3: [
    {
      id: 2,
      planId: 3,
      tradeDate: '2026-04-05',
      direction: 'BUY',
      triggerPrice: 77.4,
      quantity: 1000,
    },
    {
      id: 3,
      planId: 3,
      tradeDate: '2026-04-15',
      direction: 'SELL',
      triggerPrice: 82.3,
      quantity: 1000,
    },
  ],
}

// 实盘记录
const mockActualTrades: ActualTrade[] = [
  {
    id: 1,
    stockCode: '000858',
    stockName: '五粮液',
    direction: 'SELL',
    price: 82.3,
    quantity: 1000,
    tradeDate: '2026-05-19',
    profitLossAmount: 4900,
    profitLossPercent: 6.5,
    createdAt: '2026-05-19T16:00:00',
  },
  {
    id: 2,
    stockCode: '600519',
    stockName: '贵州茅台',
    direction: 'BUY',
    price: 1850.0,
    quantity: 100,
    tradeDate: '2026-05-15',
    createdAt: '2026-05-15T16:00:00',
  },
  {
    id: 3,
    stockCode: '000858',
    stockName: '五粮液',
    direction: 'BUY',
    price: 77.4,
    quantity: 1000,
    tradeDate: '2026-05-10',
    createdAt: '2026-05-10T16:00:00',
  },
]

// 持仓
const mockHoldings: HoldingsResponse = {
  baselineCapital: 500000,
  planCashBalance: 483200,
  actualCashBalance: 495000,
  planHoldings: [
    {
      planId: 1,
      planName: '茅台 MA20 买入',
      stockCode: '600519',
      stockName: '贵州茅台',
      quantity: 100,
      costPrice: 1850.0,
      currentPrice: 1912.5,
      unrealizedPLAmount: 6250,
      unrealizedPLPercent: 3.38,
      holdDays: 4,
      highPrice: 1935.0,
      lowPrice: 1890.0,
      closePrice: 1912.5,
      status: 'HOLDING',
      entryDate: '2026-05-15',
    },
  ],
  actualHoldings: [
    {
      stockCode: '600519',
      stockName: '贵州茅台',
      quantity: 100,
      avgCostPrice: 1850.0,
      currentPrice: 1912.5,
      unrealizedPLAmount: 6250,
      unrealizedPLPercent: 3.38,
    },
  ],
  summary: {
    totalPlanUnrealizedPL: 6250,
    totalActualUnrealizedPL: 6250,
    holdingGap: 0,
  },
}

// 走势图快照数据
function generateTrendData() {
  const snapshots: Snapshot[] = []
  const base = new Date('2026-03-01')
  let planReturn = 0
  let actualReturn = 0

  const stocks = [
    { code: '600519', name: '贵州茅台' },
    { code: '000858', name: '五粮液' },
    { code: '601318', name: '中国平安' },
  ]

  for (let i = 0; i < 56; i++) {
    const d = new Date(base)
    d.setDate(d.getDate() + i)
    if (d.getDay() === 0 || d.getDay() === 6) continue
    const dateStr = d.toISOString().slice(0, 10)
    const planDelta = (Math.random() - 0.45) * 0.6
    const actualDelta = (Math.random() - 0.4) * 0.7
    planReturn += planDelta
    actualReturn += actualDelta
    stocks.forEach((s, idx) => {
      snapshots.push({
        id: snapshots.length + 1,
        planId: idx + 1,
        snapshotDate: dateStr,
        stockCode: s.code,
        stockName: s.name,
        planStatus: idx === 0 ? 'HOLDING' : idx === 1 ? 'CLOSED' : 'PENDING',
        planReturnPercent: parseFloat((planReturn + idx * 0.2).toFixed(2)),
        actualReturnPercent: parseFloat((actualReturn + idx * 0.15).toFixed(2)),
      })
    })
  }
  return snapshots
}

const mockSnapshots = generateTrendData()

// 周期汇总
const mockPeriodSummary: Record<string, PeriodSummary> = {
  WEEK: {
    period: 'WEEK',
    planReturnPercent: 1.2,
    actualReturnPercent: 1.8,
    gap: -0.6,
  },
  MONTH: {
    period: 'MONTH',
    planReturnPercent: 4.5,
    actualReturnPercent: 3.1,
    gap: 1.4,
  },
  YEAR: {
    period: 'YEAR',
    planReturnPercent: 8.3,
    actualReturnPercent: 6.1,
    gap: 2.2,
  },
}

// API Mock 实现
export const mockApi = {
  // System Config
  getSystemConfig: async () => {
    await delay(300)
    return mockSystemConfig
  },
  updateBaselineCapital: async (value: number) => {
    await delay(400)
    mockSystemConfig.baselineCapital = value
    return { ...mockSystemConfig }
  },

  // Plan Account
  getPlanAccount: async () => {
    await delay(200)
    return mockPlanAccount
  },

  // Actual Account
  getActualAccount: async () => {
    await delay(200)
    return mockActualAccount
  },

  // Plans
  getPlans: async (params?: { status?: string; stockCode?: string }) => {
    await delay(400)
    let result = [...mockPlans]
    if (params?.status) {
      result = result.filter((p) => p.status === params.status)
    }
    if (params?.stockCode) {
      result = result.filter((p) =>
        p.stockCode.toLowerCase().includes(params.stockCode!.toLowerCase())
      )
    }
    return result
  },
  getPlan: async (id: number) => {
    await delay(300)
    const plan = mockPlans.find((p) => p.id === id)
    if (!plan) throw new Error('预案不存在')
    return plan
  },
  createPlan: async (data: Partial<Plan>) => {
    await delay(500)
    const newPlan: Plan = {
      id: mockPlans.length + 1,
      name: data.name || '',
      stockCode: data.stockCode || '',
      stockName: data.stockName || '',
      cycle: data.cycle || 'DAILY',
      status: 'PENDING',
      validUntil: data.validUntil || '',
      shares: data.shares || 100,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      conditions: [],
    }
    mockPlans.push(newPlan)
    return newPlan
  },
  updatePlan: async (id: number, data: Partial<Plan>) => {
    await delay(500)
    const idx = mockPlans.findIndex((p) => p.id === id)
    if (idx === -1) throw new Error('预案不存在')
    mockPlans[idx] = { ...mockPlans[idx], ...data, updatedAt: new Date().toISOString() }
    return mockPlans[idx]
  },
  deletePlan: async (id: number) => {
    await delay(300)
    const idx = mockPlans.findIndex((p) => p.id === id)
    if (idx === -1) throw new Error('预案不存在')
    mockPlans.splice(idx, 1)
  },

  // Plan Executions
  getPlanExecutions: async (planId: number) => {
    await delay(300)
    return mockExecutions[planId] || []
  },

  // Actual Trades
  getActualTrades: async (params?: { stockCode?: string; direction?: string }) => {
    await delay(400)
    let result = [...mockActualTrades]
    if (params?.stockCode) {
      result = result.filter((t) =>
        t.stockCode.toLowerCase().includes(params.stockCode!.toLowerCase())
      )
    }
    if (params?.direction) {
      result = result.filter((t) => t.direction === params.direction)
    }
    return result
  },
  createActualTrade: async (data: Partial<ActualTrade>) => {
    await delay(500)
    const newTrade: ActualTrade = {
      id: mockActualTrades.length + 1,
      stockCode: data.stockCode || '',
      stockName: data.stockName || '',
      direction: data.direction || 'BUY',
      price: data.price || 0,
      quantity: data.quantity || 0,
      tradeDate: data.tradeDate || '',
      createdAt: new Date().toISOString(),
    }
    mockActualTrades.push(newTrade)
    return newTrade
  },
  updateActualTrade: async (id: number, data: Partial<ActualTrade>) => {
    await delay(500)
    const idx = mockActualTrades.findIndex((t) => t.id === id)
    if (idx === -1) throw new Error('实盘记录不存在')
    mockActualTrades[idx] = { ...mockActualTrades[idx], ...data }
    return mockActualTrades[idx]
  },
  deleteActualTrade: async (id: number) => {
    await delay(300)
    const idx = mockActualTrades.findIndex((t) => t.id === id)
    if (idx === -1) throw new Error('实盘记录不存在')
    mockActualTrades.splice(idx, 1)
  },

  // Holdings
  getHoldings: async (refresh?: boolean) => {
    await delay(500)
    return mockHoldings
  },

  // Period Summary
  getPeriodSummary: async (period: string) => {
    await delay(300)
    return mockPeriodSummary[period] || mockPeriodSummary.MONTH
  },

  // Snapshots
  getSnapshots: async (params?: { snapshotDate?: string; planId?: number }) => {
    await delay(400)
    let result = [...mockSnapshots]
    if (params?.snapshotDate) {
      result = result.filter((s) => s.snapshotDate === params.snapshotDate)
    }
    if (params?.planId) {
      result = result.filter((s) => s.planId === params.planId)
    }
    return result
  },
}
