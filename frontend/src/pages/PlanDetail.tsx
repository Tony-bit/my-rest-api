import { useParams, Link, useNavigate } from 'react-router-dom'
import StatusBadge from '@/components/common/StatusBadge'
import LoadingSkeleton from '@/components/common/LoadingSkeleton'
import EmptyState from '@/components/common/EmptyState'
import ErrorAlert from '@/components/common/ErrorAlert'
import { usePlan, usePlanExecutions, useDeletePlan } from '@/hooks'
import type { PlanStatus, PlanType } from '@/types'

const CYCLE_LABEL: Record<string, string> = { DAILY: '日度', WEEKLY: '周度', MONTHLY: '月度' }
const PLAN_TYPE_STYLES: Record<PlanType, { label: string; color: string }> = {
  BUY: { label: '买入', color: 'bg-blue-900 text-blue-300 border-blue-700' },
  SELL: { label: '卖出', color: 'bg-orange-900 text-orange-300 border-orange-700' },
}

export default function PlanDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const planId = parseInt(id ?? '0', 10)

  const { data: plan, isLoading, error } = usePlan(planId)
  const { data: executions } = usePlanExecutions(planId)
  const deleteMutation = useDeletePlan()

  const handleDelete = async () => {
    if (!confirm('确认删除该预案？')) return
    try {
      await deleteMutation.mutateAsync(planId)
      navigate('/plans')
    } catch {
      // handled
    }
  }

  if (isLoading) {
    return (
      <div className="p-6 space-y-4 max-w-2xl">
        <LoadingSkeleton className="h-6 w-32" />
        <LoadingSkeleton className="h-48 w-full" />
      </div>
    )
  }

  if (error || !plan) {
    return (
      <div className="p-6 max-w-2xl">
        <ErrorAlert message={(error as Error)?.message ?? '预案不存在'} />
      </div>
    )
  }

  const planTypeStyle = PLAN_TYPE_STYLES[plan.planType as PlanType] || { label: '买入', color: 'bg-gray-800 text-gray-400' }

  return (
    <div className="p-6 space-y-4 max-w-2xl">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="text-gray-400 hover:text-gray-200">←</button>
          <h2 className="text-base font-medium text-gray-200">预案详情</h2>
        </div>
        <div className="flex items-center gap-2">
          {plan.planType === 'BUY' && plan.status === 'HOLDING' && (
            <Link
              to={`/plans/sell/new?buyPlanId=${plan.id}`}
              className="px-3 py-1.5 text-sm bg-orange-600 hover:bg-orange-500 text-white rounded transition-colors"
            >
              + 建卖出预案
            </Link>
          )}
          {plan.status === 'PENDING' && (
            <Link
              to={`/plans/${plan.id}/edit`}
              className="px-3 py-1.5 text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 rounded border border-gray-700 transition-colors"
            >
              编辑
            </Link>
          )}
          {(plan.status === 'EXPIRED' || plan.status === 'CLOSED') && (
            <button
              onClick={handleDelete}
              className="px-3 py-1.5 text-sm bg-red-950 hover:bg-red-900 text-red-400 rounded border border-red-900 transition-colors"
            >
              删除
            </button>
          )}
        </div>
      </div>

      {/* 基本信息 */}
      <section className="bg-gray-900 border border-gray-800 rounded-lg p-5 space-y-2">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-medium text-gray-300">基本信息</h3>
          <span className={`px-2 py-0.5 text-xs rounded border ${planTypeStyle.color}`}>
            {planTypeStyle.label}预案
          </span>
        </div>
        <DetailRow label="预案名称" value={plan.name} />
        <DetailRow label="股票" value={`${plan.stockCode} ${plan.stockName}`} />
        <DetailRow label="周期" value={CYCLE_LABEL[plan.cycle] ?? plan.cycle} />
        <DetailRow label="有效期至" value={plan.validUntil || '-'} />
        {plan.planType === 'BUY' && (
          <DetailRow label="买入股数" value={`${plan.executionQuantity} 股`} />
        )}
        {plan.planType === 'SELL' && (
          <DetailRow label="关联买入预案" value={plan.tradePlanId ? `#${plan.tradePlanId}` : '-'} />
        )}
        <DetailRow label="创建时间" value={plan.createdAt.slice(0, 16).replace('T', ' ')} />
        <div className="flex justify-between items-center py-1">
          <span className="text-xs text-gray-500">状态</span>
          <StatusBadge status={plan.status as PlanStatus} />
        </div>

        {/* HOLDING 状态显示持仓信息 (BUY plan only) */}
        {plan.status === 'HOLDING' && plan.planType === 'BUY' && (
          <div className="mt-3 pt-3 border-t border-gray-800 space-y-1">
            <p className="text-xs text-gray-500 mb-2">当前持股</p>
            <DetailRow label="建仓日期" value={plan.triggerDate || '-'} />
            <DetailRow label="持股数量" value={`${plan.executionQuantity} 股`} />
          </div>
        )}
      </section>

      {/* 触发条件 */}
      <section className="bg-gray-900 border border-gray-800 rounded-lg p-5 space-y-2">
        <h3 className="text-sm font-medium text-gray-300 mb-3">
          {plan.planType === 'BUY' ? '买入条件' : '卖出条件'}
        </h3>
        {!plan.condition ? (
          <p className="text-sm text-gray-600">暂无触发条件</p>
        ) : (
          <div className="flex items-center gap-2 text-sm">
            <span className={`px-2 py-0.5 text-xs rounded ${plan.planType === 'BUY' ? 'bg-green-950 text-green-400' : 'bg-red-950 text-red-400'}`}>
              {plan.planType === 'BUY' ? '买入' : '卖出'}
            </span>
            <span className="text-gray-300">
              {plan.condition.conditionType === 'MA'
                ? `MA${plan.condition.maPeriod} 触碰（容差 ±0.3%）`
                : `固定价格 ¥${plan.condition.targetPrice?.toFixed(2)}`}
            </span>
          </div>
        )}
      </section>

      {/* 执行记录 */}
      <section className="bg-gray-900 border border-gray-800 rounded-lg p-5">
        <h3 className="text-sm font-medium text-gray-300 mb-3">执行记录</h3>
        {!executions?.length ? (
          <EmptyState message="暂无执行记录" />
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-gray-500 border-b border-gray-800">
                <th className="pb-2">日期</th>
                <th className="pb-2">类型</th>
                <th className="pb-2">触发价格</th>
                <th className="pb-2">数量</th>
              </tr>
            </thead>
            <tbody>
              {executions.map((e) => (
                <tr key={e.id} className="border-t border-gray-800">
                  <td className="py-2 text-gray-400">{e.tradeDate}</td>
                  <td className="py-2">
                    <span className={`px-2 py-0.5 text-xs rounded ${plan.planType === 'BUY' ? 'bg-green-950 text-green-400' : 'bg-red-950 text-red-400'}`}>
                      {plan.planType === 'BUY' ? '买入' : '卖出'}
                    </span>
                  </td>
                  <td className="py-2 text-gray-300">¥{e.triggerPrice.toFixed(2)}</td>
                  <td className="py-2 text-gray-400">{plan.executionQuantity}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between items-center py-1">
      <span className="text-xs text-gray-500">{label}</span>
      <span className="text-sm text-gray-300">{value}</span>
    </div>
  )
}
