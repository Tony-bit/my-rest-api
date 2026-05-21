import client from './client'
import type { ApiResponse, ActualTrade } from '@/types'

export const actualTradeApi = {
  list: (params?: {
    stockCode?: string
    direction?: string
    startDate?: string
    endDate?: string
  }) =>
    client
      .get<ApiResponse<ActualTrade[]>>('/actual-trades', { params })
      .then((r) => r.data.data),

  get: (id: number) =>
    client.get<ApiResponse<ActualTrade>>(`/actual-trades/${id}`).then((r) => r.data.data),

  create: (data: CreateTradeRequest) =>
    client
      .post<ApiResponse<ActualTrade>>('/actual-trades', data)
      .then((r) => r.data.data),

  update: (id: number, data: UpdateTradeRequest) =>
    client
      .put<ApiResponse<ActualTrade>>(`/actual-trades/${id}`, data)
      .then((r) => r.data.data),

  delete: (id: number) =>
    client.delete(`/actual-trades/${id}`).then((r) => r.data),
}

export interface CreateTradeRequest {
  stockCode: string
  stockName: string
  direction: 'BUY' | 'SELL'
  price: number
  quantity: number
  tradeDate: string
}

export interface UpdateTradeRequest {
  stockName: string
  direction: 'BUY' | 'SELL'
  price: number
  quantity: number
  tradeDate: string
}
