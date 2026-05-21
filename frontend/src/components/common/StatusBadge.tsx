import type { PlanStatus } from '@/types'

const config: Record<PlanStatus, { label: string; color: string; bg: string }> = {
  PENDING: { label: '待触发', color: 'text-green-400', bg: 'bg-green-950 border-green-900' },
  HOLDING: { label: '持股中', color: 'text-amber-400', bg: 'bg-amber-950 border-amber-900' },
  CLOSED: { label: '已完成', color: 'text-blue-400', bg: 'bg-blue-950 border-blue-900' },
  EXPIRED: { label: '已过期', color: 'text-gray-400', bg: 'bg-gray-800 border-gray-700' },
}

interface Props {
  status: PlanStatus
  className?: string
}

export default function StatusBadge({ status, className = '' }: Props) {
  const c = config[status]
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 text-xs font-medium rounded border ${c.bg} ${c.color} ${className}`}
    >
      {c.label}
    </span>
  )
}
