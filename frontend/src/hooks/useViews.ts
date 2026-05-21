import { useQuery } from '@tanstack/react-query'
import { viewApi } from '@/api'

export const HOLDINGS_KEY = ['holdings'] as const
export const PERIOD_KEY = (period: string) => ['period-summary', period] as const
export const SNAPSHOTS_KEY = (filters?: Record<string, unknown>) =>
  ['snapshots', filters ?? {}] as const

export function useHoldings(refresh?: boolean) {
  return useQuery({
    queryKey: [...HOLDINGS_KEY, refresh],
    queryFn: () => viewApi.getHoldings(refresh),
  })
}

export function usePeriodSummary(period: string) {
  return useQuery({
    queryKey: PERIOD_KEY(period),
    queryFn: () => viewApi.getPeriodSummary(period),
    enabled: !!period,
  })
}

export function useSnapshots(filters?: { date?: string; planId?: number }) {
  return useQuery({
    queryKey: SNAPSHOTS_KEY(filters ?? {}),
    queryFn: () => viewApi.getSnapshots(filters),
  })
}
