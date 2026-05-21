import client from './client'
import type { ApiResponse, HoldingsResponse, PeriodSummary, Snapshot } from '@/types'

export const viewApi = {
  getHoldings: (refresh?: boolean) =>
    client
      .get<ApiResponse<HoldingsResponse>>('/views/holdings', {
        params: { refresh: refresh ?? false },
      })
      .then((r) => r.data.data),

  getPeriodSummary: (period: string) =>
    client
      .get<ApiResponse<PeriodSummary>>('/views/period-summary', {
        params: { period },
      })
      .then((r) => r.data.data),

  getSnapshots: (params?: { date?: string; planId?: number }) =>
    client
      .get<ApiResponse<Snapshot[]>>('/snapshots', { params })
      .then((r) => r.data.data),
}
