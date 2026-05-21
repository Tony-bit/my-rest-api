import BaselineCapitalInput from '@/components/views/BaselineCapitalInput'
import LoadingSkeleton from '@/components/common/LoadingSkeleton'
import { useActualAccount, useHoldings } from '@/hooks'

export default function Settings() {
  const { data: actualAccount, isLoading: accountLoading } = useActualAccount()
  const { data: holdings, isLoading: holdingsLoading } = useHoldings()

  const planMarketValue = holdings
    ? holdings.planHoldings.reduce((s, h) => s + h.currentPrice * h.quantity, 0)
    : 0
  const planTotalAssets = (holdings?.planCashBalance ?? 0) + planMarketValue

  return (
    <div className="p-6 space-y-6">
      <h2 className="text-base font-medium text-gray-200">系统设置</h2>

      <section className="bg-gray-900 border border-gray-800 rounded-lg p-5 space-y-4">
        <h3 className="text-sm font-medium text-gray-300">基准资金</h3>
        <BaselineCapitalInput
          planCashBalance={holdings?.planCashBalance}
          readonly
        />
      </section>

      <section className="bg-gray-900 border border-gray-800 rounded-lg p-5 space-y-3">
        <h3 className="text-sm font-medium text-gray-300">预案账户</h3>
        {holdingsLoading ? (
          <div className="space-y-2">
            <LoadingSkeleton className="h-4 w-48" />
            <LoadingSkeleton className="h-4 w-32" />
          </div>
        ) : (
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">当前现金余额</dt>
              <dd className="text-gray-300">
                ¥{(holdings?.planCashBalance ?? 0).toLocaleString('zh-CN')}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">当前持仓市值</dt>
              <dd className="text-gray-300">
                ¥{planMarketValue.toLocaleString('zh-CN')}
              </dd>
            </div>
            <div className="flex justify-between border-t border-gray-800 pt-2">
              <dt className="text-gray-400">总资产</dt>
              <dd className="text-gray-200 font-medium">
                ¥{planTotalAssets.toLocaleString('zh-CN')}
              </dd>
            </div>
          </dl>
        )}
      </section>

      <section className="bg-gray-900 border border-gray-800 rounded-lg p-5 space-y-3">
        <h3 className="text-sm font-medium text-gray-300">实盘账户</h3>
        {accountLoading ? (
          <LoadingSkeleton className="h-4 w-48" />
        ) : (
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">当前现金余额</dt>
              <dd className="text-gray-300">
                ¥{(actualAccount?.cashBalance ?? 0).toLocaleString('zh-CN')}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">当前持仓市值</dt>
              <dd className="text-gray-300">
                ¥{(holdings?.actualHoldings ?? [])
                  .reduce((s, h) => s + h.currentPrice * h.quantity, 0)
                  .toLocaleString('zh-CN')}
              </dd>
            </div>
            <div className="flex justify-between border-t border-gray-800 pt-2">
              <dt className="text-gray-400">总资产</dt>
              <dd className="text-gray-200 font-medium">
                ¥{(holdings?.summary.actualTotalValue ?? 0).toLocaleString('zh-CN')}
              </dd>
            </div>
            <p className="text-xs text-gray-600 pt-1">由系统自动同步，不可单独修改</p>
          </dl>
        )}
      </section>
    </div>
  )
}
