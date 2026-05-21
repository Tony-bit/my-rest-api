import { useState, useMemo } from 'react'
import TrendChart from '@/components/views/TrendChart'
import BaselineCapitalInput from '@/components/views/BaselineCapitalInput'
import LoadingSkeleton from '@/components/common/LoadingSkeleton'
import { useDashboard, useHoldings } from '@/hooks'

type Period = 'WEEK' | 'MONTH' | 'YEAR'
const PERIODS: { value: Period; label: string }[] = [
  { value: 'WEEK', label: '本周' },
  { value: 'MONTH', label: '本月' },
  { value: 'YEAR', label: '本年' },
]

export default function Dashboard() {
  const [period, setPeriod] = useState<Period>('MONTH')

  const { data: dashboard, isLoading: dashboardLoading } = useDashboard()
  const { data: holdings, isLoading: holdingsLoading } = useHoldings()

  const kpis = dashboard?.kpis
  const trendData = useMemo(() => {
    if (!dashboard?.trend || dashboard.trend.length === 0) return []
    return dashboard.trend.map((t) => ({
      date: t.date,
      planCumulative: parseFloat((t.planReturnPercent ?? 0).toFixed(2)),
      actualCumulative: parseFloat((t.actualReturnPercent ?? 0).toFixed(2)),
    }))
  }, [dashboard?.trend])

  const planReturnPct = kpis?.planReturnPercent ?? 0
  const actualReturnPct = kpis?.actualReturnPercent ?? 0
  const gap = kpis?.holdingGap ?? 0

  const fmt = (n: number) => (n >= 0 ? `+${n.toFixed(2)}%` : `${n.toFixed(2)}%`)

  return (
    <div className="p-6 space-y-6">
      <h2 className="text-base font-medium text-gray-200">对比分析</h2>

      <BaselineCapitalInput
        planCashBalance={kpis?.planCashBalance}
        actualCashBalance={kpis?.actualCashBalance}
        readonly={true}
      />

      {/* KPI 卡片 */}
      <div className="grid grid-cols-3 gap-4">
        <KpiCard
          label="预案收益"
          value={fmt(planReturnPct)}
          valueColor={planReturnPct >= 0 ? 'text-green-400' : 'text-red-400'}
          sub={`预案资金 ¥${((holdings?.summary?.planTotalValue) ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })}`}
          loading={dashboardLoading}
        />
        <KpiCard
          label="实盘收益"
          value={fmt(actualReturnPct)}
          valueColor={actualReturnPct >= 0 ? 'text-orange-400' : 'text-red-400'}
          sub={`实盘资金 ¥${((holdings?.summary?.actualTotalValue) ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })}`}
          loading={dashboardLoading}
        />
        <KpiCard
          label="知行差"
          value={fmt(gap)}
          valueColor={gap >= 0 ? 'text-green-400' : 'text-red-400'}
          sub={gap >= 0 ? '预案更优' : '实盘更优'}
          loading={dashboardLoading}
        />
      </div>

      {/* 走势图 */}
      <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
        <div className="flex items-center justify-between mb-4">
          <p className="text-sm font-medium text-gray-300">预案 vs 实盘 累计收益走势</p>
          <div className="flex gap-1">
            {PERIODS.map((p) => (
              <button
                key={p.value}
                onClick={() => setPeriod(p.value)}
                className={`px-3 py-1 text-xs rounded transition-colors ${
                  period === p.value
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
                }`}
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>
        {!dashboardLoading && trendData.length > 0 ? (
          <TrendChart data={trendData} />
        ) : (
          <div className="h-64 flex items-center justify-center text-gray-600 text-sm">
            {dashboardLoading ? '加载中…' : '暂无走势数据'}
          </div>
        )}
      </div>

      {/* 持仓面板 */}
      <div className="grid grid-cols-2 gap-4">
        <HoldingPanel
          title="当前持仓（预案）"
          holdings={
            (holdings?.planHoldings ?? []).map((h) => ({
              stockCode: h.stockCode,
              stockName: h.stockName ?? '',
              unrealizedPLAmount: h.unrealizedPLAmount,
              unrealizedPLPercent: h.unrealizedPLPercent,
              quantity: h.quantity,
            }))
          }
          loading={holdingsLoading}
          linkTo="/plans"
        />
        <HoldingPanel
          title="当前持仓（实盘）"
          holdings={
            (holdings?.actualHoldings ?? []).map((h) => ({
              stockCode: h.stockCode,
              stockName: h.stockName ?? '',
              unrealizedPLAmount: h.unrealizedPLAmount,
              unrealizedPLPercent: h.unrealizedPLPercent,
              quantity: h.quantity,
            }))
          }
          loading={holdingsLoading}
          linkTo="/actual-trades"
        />
      </div>
    </div>
  )
}

function KpiCard({
  label,
  value,
  valueColor,
  sub,
  loading,
}: {
  label: string
  value: string
  valueColor: string
  sub: string
  loading: boolean
}) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
      <p className="text-xs text-gray-500 mb-1">{label}</p>
      {loading ? (
        <LoadingSkeleton className="h-8 w-24 mb-1" />
      ) : (
        <p className={`text-2xl font-medium ${valueColor}`}>{value}</p>
      )}
      {loading ? (
        <LoadingSkeleton className="h-3 w-32" />
      ) : (
        <p className="text-xs text-gray-600 mt-1">{sub}</p>
      )}
    </div>
  )
}

function HoldingPanel({
  title,
  holdings,
  loading,
  linkTo,
}: {
  title: string
  holdings: {
    stockCode: string
    stockName: string
    unrealizedPLAmount: number
    unrealizedPLPercent: number
    quantity: number
  }[]
  loading: boolean
  linkTo: string
}) {
  const totalPL = holdings.reduce((s, h) => s + h.unrealizedPLAmount, 0)
  const fmtPL = (n: number) =>
    n >= 0
      ? `+¥${n.toLocaleString('zh-CN')}`
      : `-¥${Math.abs(n).toLocaleString('zh-CN')}`

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
      <div className="flex items-center justify-between mb-2">
        <p className="text-xs text-gray-500">{title}</p>
        <a
          href={linkTo}
          className="text-xs text-blue-400 hover:text-blue-300"
        >
          查看详情 →
        </a>
      </div>
      {loading ? (
        <LoadingSkeleton className="h-8 w-40" />
      ) : holdings.length === 0 ? (
        <p className="text-sm text-gray-600 py-4 text-center">暂无持仓</p>
      ) : (
        <>
          <p className="text-sm text-gray-300">
            持仓 {holdings.length} 只，总浮盈{' '}
            <span className={totalPL >= 0 ? 'text-green-400' : 'text-red-400'}>
              {fmtPL(totalPL)}
            </span>
          </p>
          <div className="mt-2 space-y-1">
            {holdings.slice(0, 3).map((h) => (
              <div key={h.stockCode} className="flex justify-between text-xs">
                <span className="text-gray-400">
                  {h.stockCode} {h.stockName}
                </span>
                <span className={h.unrealizedPLAmount >= 0 ? 'text-green-400' : 'text-red-400'}>
                  {fmtPL(h.unrealizedPLAmount)} ({h.unrealizedPLPercent >= 0 ? '+' : ''}
                  {h.unrealizedPLPercent.toFixed(2)}%)
                </span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
