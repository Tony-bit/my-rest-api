export interface SystemConfig {
  id: number
  baselineCapital: number
  updatedAt: string
}

export interface UpdateBaselineCapitalRequest {
  baselineCapital: number
}
