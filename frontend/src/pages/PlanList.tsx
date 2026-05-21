import { useState } from 'react'
import { Link } from 'react-router-dom'
import StatusBadge from '@/components/common/StatusBadge'
import LoadingSkeleton from '@/components/common/LoadingSkeleton'
import EmptyState from '@/components/common/EmptyState'
import ErrorAlert from '@/components/common/ErrorAlert'
import { usePlans, useDeletePlan } from '@/hooks'
import type { PlanStatus } from '@/types'

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待触发' },
  { value: 'HOLDING', label: '持股中' },
  { value: 'CLOSED', label: '已完成' },
  { value: 'EXPIRED', label: '已过期' },
]

export default function PlanList() {
  const [statusFilter, setStatusFilter] = useState('')
  const [stockCodeSearch, setStockCodeSearch] = useState('')

  const { data: plans, isLoading, error } = usePlans({
    status: statusFilter || undefined,
    stockCode: stockCodeSearch || undefined,
  })
  const deleteMutation = useDeletePlan()

  const handleDelete = async (id: number) => {
    if (!confirm('确认删除该预案？')) return
    try {
      await deleteMutation.mutateAsync(id)
    } catch {
      // handled by hook
    }
  }

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-medium text-gray-200">预案管理</h2>
        <Link
          to="/plans/new"
          className="px-3 py-1.5 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded transition-colors"
        >
          + 创建预案
        </Link>
      </div>

      {/* 筛选栏 */}
      <div className="flex items-center gap-3">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-1.5 text-sm bg-gray-900 border border-gray-700 rounded text-gray-300 focus:outline-none focus:border-blue-500"
        >
          {STATUS_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
        <input
          type="text"
          value={stockCodeSearch}
          onChange={(e) => setStockCodeSearch(e.target.value)}
          placeholder="股票代码"
          className="px-3 py-1.5 text-sm bg-gray-900 border border-gray-700 rounded text-gray-300 placeholder-gray-600 focus:outline-none focus:border-blue-500 w-36"
        />
        <button
          onClick={() => {}}
          className="px-3 py-1.5 text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 rounded border border-gray-700 transition-colors"
        >
          搜索
        </button>
      </div>

      {error && <ErrorAlert message={(error as Error).message} />}

      {/* 表格 */}
      <div className="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-gray-500 border-b border-gray-800">
                <th className="px-4 py-3 font-medium">名称</th>
                <th className="px-4 py-3 font-medium">股票</th>
                <th className="px-4 py-3 font-medium">周期</th>
                <th className="px-4 py-3 font-medium">状态</th>
                <th className="px-4 py-3 font-medium">创建时间</th>
                <th className="px-4 py-3 font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                Array.from({ length: 3 }).map((_, i) => (
                  <tr key={i} className="border-t border-gray-800">
                    {Array.from({ length: 6 }).map((_, j) => (
                      <td key={j} className="px-4 py-3">
                        <LoadingSkeleton className="h-4 w-24" />
                      </td>
                    ))}
                  </tr>
                ))
              ) : !plans?.length ? (
                <tr>
                  <td colSpan={6}>
                    <EmptyState message="暂无预案记录" />
                  </td>
                </tr>
              ) : (
                plans.map((plan) => (
                  <tr
                    key={plan.id}
                    className="border-t border-gray-800 hover:bg-gray-800/50 transition-colors"
                  >
                    <td className="px-4 py-3 text-gray-200">{plan.name}</td>
                    <td className="px-4 py-3 text-gray-400">
                      {plan.stockCode} {plan.stockName}
                    </td>
                    <td className="px-4 py-3 text-gray-400">{plan.cycle}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={plan.status as PlanStatus} />
                    </td>
                    <td className="px-4 py-3 text-gray-500">{plan.createdAt.slice(0, 10)}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <Link
                          to={`/plans/${plan.id}`}
                          className="text-xs text-blue-400 hover:text-blue-300"
                        >
                          详情
                        </Link>
                        {plan.status === 'PENDING' && (
                          <Link
                            to={`/plans/${plan.id}/edit`}
                            className="text-xs text-gray-400 hover:text-gray-300"
                          >
                            编辑
                          </Link>
                        )}
                        {plan.status === 'EXPIRED' && (
                          <button
                            onClick={() => handleDelete(plan.id)}
                            className="text-xs text-red-400 hover:text-red-300"
                          >
                            删除
                          </button>
                        )}
                      </div>
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
