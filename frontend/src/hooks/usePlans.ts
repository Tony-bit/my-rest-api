import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { planApi, type CreatePlanRequest, type UpdatePlanRequest } from '@/api'

export const PLAN_KEYS = {
  all: ['plans'] as const,
  list: (filters?: Record<string, string>) => ['plans', 'list', filters ?? {}] as const,
  detail: (id: number) => ['plans', 'detail', id] as const,
  conditions: (planId: number) => ['plans', 'conditions', planId] as const,
  executions: (planId: number) => ['plans', 'executions', planId] as const,
}

export function usePlans(filters?: { status?: string; stockCode?: string }) {
  return useQuery({
    queryKey: PLAN_KEYS.list(filters ?? {}),
    queryFn: () => planApi.list(filters),
  })
}

export function usePlan(id: number) {
  return useQuery({
    queryKey: PLAN_KEYS.detail(id),
    queryFn: () => planApi.get(id),
    enabled: id > 0,
  })
}

export function useCreatePlan() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreatePlanRequest) => planApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: PLAN_KEYS.all }),
  })
}

export function useUpdatePlan() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdatePlanRequest }) =>
      planApi.update(id, data),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: PLAN_KEYS.all })
      qc.invalidateQueries({ queryKey: PLAN_KEYS.detail(vars.id) })
    },
  })
}

export function useDeletePlan() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => planApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: PLAN_KEYS.all }),
  })
}

export function useTriggerPlan() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, targetDate }: { id: number; targetDate?: string }) =>
      planApi.trigger(id, targetDate),
    onSuccess: () => qc.invalidateQueries({ queryKey: PLAN_KEYS.all }),
  })
}

export function useBatchTrigger() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (targetDate: string) => planApi.batchTrigger(targetDate),
    onSuccess: () => qc.invalidateQueries({ queryKey: PLAN_KEYS.all }),
  })
}
