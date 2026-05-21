import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { systemConfigApi } from '@/api'

export const SYSTEM_CONFIG_KEY = ['system-config'] as const
export const PLAN_ACCOUNT_KEY = ['plan-account'] as const

export function useSystemConfig() {
  return useQuery({
    queryKey: SYSTEM_CONFIG_KEY,
    queryFn: () => systemConfigApi.get(),
    staleTime: 1000 * 60,
  })
}

export function useUpdateBaselineCapital() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (baselineCapital: number) =>
      systemConfigApi.updateBaselineCapital(baselineCapital),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: SYSTEM_CONFIG_KEY })
    },
  })
}
