import { useState } from 'react'
import LoadingSkeleton from '@/components/common/LoadingSkeleton'
import EmptyState from '@/components/common/EmptyState'
import ErrorAlert from '@/components/common/ErrorAlert'
import { useSnapshots } from '@/hooks'

export default function SnapshotList() {
  const [dateFilter, setDateFilter] = useState('')
  const [planIdFilter, setPlanIdFilter] = useState('')

  const { data: snapshots, isLoading, error } = useSnapshots({
    date: dateFilter || undefined,
    planId: planIdFilter ? parseInt(planIdFilter) : undefined,
  })

  return (
    <div className="p-6 space-y-4">
      <h2 className="text-base font-medium text-gray-200">历史快照</h2>

      <div className="flex items-center gap-3">
        <input
          type="date"
          value={dateFilter}
          onChange={(e) => setDateFilter(e.target.value)}
          className="px-3 py-1.5 text-sm bg-gray-900 border border-gray-700 rounded text-gray-300 focus:outline-none focus:border-blue-500"
        />
        <input
          type="number"
          value={planIdFilter}
          onChange={(e) => setPlanIdFilter(e.target.value)}
          placeholder="预案ID"
          className="px-3 py-1.5 text-sm bg-gray-900 border border-gray-700 rounded text-gray-300 placeholder-gray-600 focus:outline-none focus:border-blue-500 w-28"
        />
        <button
          onClick={() => {}}
          className="px-3 py-1.5 text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 rounded border border-gray-700 transition-colors"
        >
          查询
        </button>
      </div>

      {error && <ErrorAlert message={(error as Error).message} />}

      <div className="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-gray-500 border-b border-gray-800">
                <th className="px-4 py-3 font-medium">日期</th>
                <th className="px-4 py-3 font-medium">股票</th>
                <th className="px-4 py-3 font-medium">预案状态</th>
                <th className="px-4 py-3 font-medium">预案收益</th>
                <th className="px-4 py-3 font-medium">实盘收益</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                Array.from({ length: 4 }).map((_, i) => (
                  <tr key={i} className="border-t border-gray-800">
                    {Array.from({ length: 5 }).map((_, j) => (
                      <td key={j} className="px-4 py-3">
                        <LoadingSkeleton className="h-4 w-20" />
                      </td>
                    ))}
                  </tr>
                ))
              ) : !snapshots?.length ? (
                <tr>
                  <td colSpan={5}>
                    <EmptyState message="暂无快照数据" />
                  </td>
                </tr>
              ) : (
                snapshots.map((s) => (
                  <tr
                    key={s.id}
                    className="border-t border-gray-800 hover:bg-gray-800/50 transition-colors"
                  >
                    <td className="px-4 py-3 text-gray-400">{s.snapshotDate}</td>
                    <td className="px-4 py-3 text-gray-300">
                      {s.stockCode} {s.stockName}
                    </td>
                    <td className="px-4 py-3">
                      <StatusTag status={s.planStatus} />
                    </td>
                    <td className="px-4 py-3">
                      <span className={s.planReturnPercent >= 0 ? 'text-green-400' : 'text-red-400'}>
                        {s.planReturnPercent >= 0 ? '+' : ''}{s.planReturnPercent.toFixed(2)}%
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-400">
                      {s.actualReturnPercent != null && s.actualReturnPercent !== 0 ? (
                        <span className={s.actualReturnPercent >= 0 ? 'text-green-400' : 'text-red-400'}>
                          {s.actualReturnPercent >= 0 ? '+' : ''}{s.actualReturnPercent.toFixed(2)}%
                        </span>
                      ) : (
                        '—'
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

function StatusTag({ status }: { status: string }) {
  const map: Record<string, string> = {
    PENDING: 'text-green-400',
    HOLDING: 'text-amber-400',
    CLOSED: 'text-blue-400',
    EXPIRED: 'text-gray-400',
  }
  const label: Record<string, string> = {
    PENDING: '待触发',
    HOLDING: '持股中',
    CLOSED: '已完成',
    EXPIRED: '已过期',
  }
  return (
    <span className={`text-xs ${map[status] ?? 'text-gray-400'}`}>
      {label[status] ?? status}
    </span>
  )
}
