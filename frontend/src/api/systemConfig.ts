import client from './client'
import type { ApiResponse, SystemConfig } from '@/types'

export const systemConfigApi = {
  get: () =>
    client.get<ApiResponse<SystemConfig>>('/system-config').then((r) => r.data.data),

  updateBaselineCapital: (baselineCapital: number) =>
    client
      .put<ApiResponse<BaselineCapitalUpdateResponse>>('/system-config/baseline-capital', {
        baselineCapital,
      })
      .then((r) => r.data.data),
}

export interface BaselineCapitalUpdateResponse {
  baselineCapital: number
  planCashBalance: number
  actualCashBalance: number
  message?: string
}
