import { useState } from 'react'
import { Link } from 'react-router-dom'
import StatusBadge from '@/components/common/StatusBadge'
import LoadingSkeleton from '@/components/common/LoadingSkeleton'
import EmptyState from '@/components/common/EmptyState'
import ErrorAlert from '@/components/common/ErrorAlert'
import { usePlans, useDeletePlan, useTriggerPlan, useBatchTrigger } from '@/hooks'
import type { PlanStatus, PlanType } from '@/types'

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待触发' },
  { value: 'HOLDING', label: '持股中' },
  { value: 'CLOSED', label: '已完成' },
  { value: 'EXPIRED', label: '已过期' },
]

const PLAN_TYPE_STYLES: Record<PlanType, { label: string; color: string }> = {
  BUY: { label: '买入', color: 'bg-blue-900 text-blue-300 border-blue-700' },
  SELL: { label: '卖出', color: 'bg-orange-900 text-orange-300 border-orange-700' },
}

const TRIGGER_STATUS_LABELS: Record<string, string> = {
  data_unavailable: '数据暂不可用',
  success: '触发成功',
  skipped: '已跳过',
  pending: '待触发',
}

export default function PlanList() {
  const [statusFilter, setStatusFilter] = useState('')
  const [stockCodeSearch, setStockCodeSearch] = useState('')
  const [batchDate, setBatchDate] = useState('')

  const { data: plans, isLoading, error } = usePlans({
    status: statusFilter || undefined,
    stockCode: stockCodeSearch || undefined,
  })
  const deleteMutation = useDeletePlan()
  const triggerMutation = useTriggerPlan()
  const batchTriggerMutation = useBatchTrigger()

  const handleDelete = async (id: number) => {
    if (!confirm('确认删除该预案？')) return
    try {
      await deleteMutation.mutateAsync(id)
    } catch {
      // handled by hook
    }
  }

  const handleTrigger = async (id: number) => {
    if (!confirm('确认触发该预案？')) return
    try {
      const result = await triggerMutation.mutateAsync({ id, targetDate: undefined })
      const statusLabel = TRIGGER_STATUS_LABELS[result.status] ?? result.status
      alert(`${result.stockName}: ${statusLabel}`)
    } catch (e) {
      alert('触发失败: ' + (e as Error).message)
    }
  }

  const handleBatchTrigger = async () => {
    if (!batchDate) {
      alert('请选择触发日期')
      return
    }
    try {
      const result = await batchTriggerMutation.mutateAsync(batchDate)
      alert(`触发完成: ${result.triggered} 成功, ${result.skipped} 跳过`)
    } catch (e) {
      alert('批量触发失败: ' + (e as Error).message)
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
      <div className="flex items-center gap-3 flex-wrap">
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
          onKeyDown={(e) => e.key === 'Enter' && setStockCodeSearch(stockCodeSearch)}
          placeholder="股票代码"
          className="px-3 py-1.5 text-sm bg-gray-900 border border-gray-700 rounded text-gray-300 placeholder-gray-600 focus:outline-none focus:border-blue-500 w-36"
        />
        <button
          onClick={() => {}}
          className="px-3 py-1.5 text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 rounded border border-gray-700 transition-colors"
        >
          搜索
        </button>

        {/* 批量触发 */}
        <div className="flex items-center gap-2 ml-auto">
          <input
            type="date"
            value={batchDate}
            onChange={(e) => setBatchDate(e.target.value)}
            className="px-3 py-1.5 text-sm bg-gray-900 border border-gray-700 rounded text-gray-300 focus:outline-none focus:border-blue-500"
          />
          <button
            onClick={handleBatchTrigger}
            disabled={batchTriggerMutation.isPending}
            className="px-3 py-1.5 text-sm bg-green-600 hover:bg-green-500 text-white rounded border border-green-700 transition-colors disabled:opacity-50"
          >
            {batchTriggerMutation.isPending ? '触发中...' : '批量触发'}
          </button>
        </div>
      </div>

      {error && <ErrorAlert message={(error as Error).message} />}

      {/* 表格 */}
      <div className="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-gray-500 border-b border-gray-800">
                <th className="px-4 py-3 font-medium">名称</th>
                <th className="px-4 py-3 font-medium">类型</th>
                <th className="px-4 py-3 font-medium">股票</th>
                <th className="px-4 py-3 font-medium">触发日期</th>
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
                    {Array.from({ length: 7 }).map((_, j) => (
                      <td key={j} className="px-4 py-3">
                        <LoadingSkeleton className="h-4 w-24" />
                      </td>
                    ))}
                  </tr>
                ))
              ) : !plans?.length ? (
                <tr>
                  <td colSpan={8}>
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
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 text-xs rounded border ${PLAN_TYPE_STYLES[plan.planType as PlanType]?.color || 'bg-gray-800 text-gray-400'}`}>
                        {plan.planType === 'BUY' ? '买入' : '卖出'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-400">
                      {plan.stockCode} {plan.stockName}
                    </td>
                    <td className="px-4 py-3 text-gray-400">{plan.triggerDate || '-'}</td>
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
                          <button
                            onClick={() => handleTrigger(plan.id)}
                            disabled={triggerMutation.isPending}
                            className="text-xs text-green-400 hover:text-green-300 disabled:opacity-50"
                          >
                            触发
                          </button>
                        )}
                        {plan.status === 'PENDING' && (
                          <Link
                            to={`/plans/${plan.id}/edit`}
                            className="text-xs text-gray-400 hover:text-gray-300"
                          >
                            编辑
                          </Link>
                        )}
                        {plan.planType === 'BUY' && plan.status === 'HOLDING' && (
                          <Link
                            to={`/plans/sell/new?buyPlanId=${plan.id}`}
                            className="text-xs text-orange-400 hover:text-orange-300"
                          >
                            建卖出预案
                          </Link>
                        )}
                        {(plan.status === 'EXPIRED' || plan.status === 'CLOSED') && (
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
