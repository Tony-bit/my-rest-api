import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '@/api'

export const DASHBOARD_KEY = ['dashboard'] as const

export function useDashboard() {
  return useQuery({
    queryKey: DASHBOARD_KEY,
    queryFn: () => dashboardApi.get(),
  })
}
