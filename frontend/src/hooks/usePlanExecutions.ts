import { useQuery } from '@tanstack/react-query'
import { planExecutionApi } from '@/api'

export const PLAN_EXECUTION_KEYS = (planId: number) =>
  ['plan-executions', planId] as const

export function usePlanExecutions(planId: number) {
  return useQuery({
    queryKey: PLAN_EXECUTION_KEYS(planId),
    queryFn: () => planExecutionApi.list(planId),
    enabled: planId > 0,
  })
}
