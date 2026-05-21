import { useQuery } from '@tanstack/react-query'
import { actualAccountApi } from '@/api'

export const ACTUAL_ACCOUNT_KEY = ['actual-account'] as const

export function useActualAccount() {
  return useQuery({
    queryKey: ACTUAL_ACCOUNT_KEY,
    queryFn: () => actualAccountApi.get(),
    staleTime: 1000 * 60,
  })
}
