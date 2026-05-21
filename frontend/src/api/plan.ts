import client from './client'
import type { ApiResponse, Plan, PlanCondition } from '@/types'

export const planApi = {
  list: (params?: { status?: string; stockCode?: string }) =>
    client
      .get<ApiResponse<Plan[]>>('/plans', { params })
      .then((r) => r.data.data),

  get: (id: number) =>
    client.get<ApiResponse<Plan>>(`/plans/${id}`).then((r) => r.data.data),

  create: (data: CreatePlanRequest) =>
    client.post<ApiResponse<Plan>>('/plans', data).then((r) => r.data.data),

  update: (id: number, data: UpdatePlanRequest) =>
    client.put<ApiResponse<Plan>>(`/plans/${id}`, data).then((r) => r.data.data),

  delete: (id: number) =>
    client.delete(`/plans/${id}`).then((r) => r.data),

  getConditions: (planId: number) =>
    client
      .get<ApiResponse<PlanCondition[]>>(`/plans/${planId}/conditions`)
      .then((r) => r.data.data),

  addCondition: (planId: number, data: CreateConditionRequest) =>
    client
      .post<ApiResponse<PlanCondition>>(`/plans/${planId}/conditions`, data)
      .then((r) => r.data.data),

  updateCondition: (planId: number, conditionId: number, data: UpdateConditionRequest) =>
    client
      .put<ApiResponse<PlanCondition>>(`/plans/${planId}/conditions/${conditionId}`, data)
      .then((r) => r.data.data),

  deleteCondition: (planId: number, conditionId: number) =>
    client.delete(`/plans/${planId}/conditions/${conditionId}`).then((r) => r.data),

  trigger: (id: number, targetDate?: string) =>
    client
      .post<ApiResponse<TriggerDetail>>(`/plans/${id}/trigger`, targetDate ? { targetDate } : {})
      .then((r) => r.data.data),

  batchTrigger: (targetDate: string) =>
    client
      .post<ApiResponse<TriggerResult>>('/trigger', { targetDate })
      .then((r) => r.data.data),
}

export interface TriggerDetail {
  planId: number
  stockName: string
  status: string
}

export interface TriggerResult {
  targetDate: string
  totalPlans: number
  triggered: number
  skipped: number
  details: TriggerDetail[]
}

export interface CreatePlanRequest {
  name: string
  stockCode: string
  stockName: string
  cycle: string
  validUntil: string
  executionQuantity: number
  conditions: CreateConditionRequest[]
}

export interface UpdatePlanRequest {
  name: string
  stockCode: string
  stockName: string
  cycle: string
  validUntil: string
  executionQuantity: number
}

export interface CreateConditionRequest {
  conditionType: 'PRICE' | 'MA'
  direction: 'BUY' | 'SELL'
  maPeriod?: number
  targetPrice?: number
}

export interface UpdateConditionRequest {
  conditionType: 'PRICE' | 'MA'
  direction: 'BUY' | 'SELL'
  maPeriod?: number | null
  targetPrice?: number | null
  isActive?: boolean
}
