import client from './client'
import type { ApiResponse } from '@/types'

export const planExecutionApi = {
  list: (planId: number) =>
    client.get<ApiResponse<PlanExecution[]>>(`/plans/${planId}/executions`).then((r) => r.data.data),
}

export interface PlanExecution {
  id: number
  planId: number
  tradeDate: string
  triggered: boolean
  triggerPrice: number
  closePrice?: number
  maValue?: number
  executed: boolean
  linkedExecutionId?: number
  createdAt: string
}
