import client from './client'
import type { ApiResponse } from '@/types'

export interface SnapshotResult {
  date: string
  planSnapshots: number
  actualSnapshots: number
}

export const snapshotApi = {
  generate: (date?: string) =>
    client
      .post<ApiResponse<SnapshotResult>>('/admin/snapshots/generate', null, {
        params: date ? { date } : {},
      })
      .then((r) => r.data.data),
}
