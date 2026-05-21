import client from './client'
import type { ApiResponse, DashboardResponse } from '@/types'

export const dashboardApi = {
  get: () =>
    client.get<ApiResponse<DashboardResponse>>('/dashboard').then((r) => r.data.data),
}
