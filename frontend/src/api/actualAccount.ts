import client from './client'
import type { ApiResponse, ActualAccount } from '@/types'

export const actualAccountApi = {
  get: () =>
    client.get<ApiResponse<ActualAccount>>('/actual-account').then((r) => r.data.data),
}
