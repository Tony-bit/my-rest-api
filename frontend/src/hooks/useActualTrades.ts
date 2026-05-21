import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  actualTradeApi,
  type CreateTradeRequest,
  type UpdateTradeRequest,
} from '@/api'

export const TRADE_KEYS = {
  all: ['actual-trades'] as const,
  list: (filters?: Record<string, string>) =>
    ['actual-trades', 'list', filters ?? {}] as const,
}

export function useActualTrades(
  filters?: { stockCode?: string; direction?: string; startDate?: string; endDate?: string }
) {
  return useQuery({
    queryKey: TRADE_KEYS.list(filters ?? {}),
    queryFn: () => actualTradeApi.list(filters),
  })
}

export function useCreateActualTrade() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateTradeRequest) => actualTradeApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: TRADE_KEYS.all }),
  })
}

export function useUpdateActualTrade() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateTradeRequest }) =>
      actualTradeApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: TRADE_KEYS.all }),
  })
}

export function useDeleteActualTrade() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => actualTradeApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: TRADE_KEYS.all }),
  })
}
