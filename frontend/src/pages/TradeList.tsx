import { useState } from 'react'
import { Link } from 'react-router-dom'
import LoadingSkeleton from '@/components/common/LoadingSkeleton'
import EmptyState from '@/components/common/EmptyState'
import ErrorAlert from '@/components/common/ErrorAlert'
import { useActualTrades, useDeleteActualTrade } from '@/hooks'

export default function TradeList() {
  const [stockCodeSearch, setStockCodeSearch] = useState('')
  const [directionFilter, setDirectionFilter] = useState('')

  const { data: trades, isLoading, error } = useActualTrades({
    stockCode: stockCodeSearch || undefined,
    direction: directionFilter || undefined,
  })
  const deleteMutation = useDeleteActualTrade()

  const handleDelete = async (id: number) => {
    if (!confirm('确认删除该记录？')) return
    try {
      await deleteMutation.mutateAsync(id)
    } catch {
      // handled
    }
  }

  const fmtPL = (pl?: number) => {
    if (pl === undefined || pl === null) return '—'
    return pl >= 0 ? `+¥${pl.toLocaleString('zh-CN')}` : `-¥${Math.abs(pl).toLocaleString('zh-CN')}`
  }
  const fmtPLPct = (pct?: number) => {
    if (pct === undefined || pct === null) return '—'
    return pct >= 0 ? `+${pct.toFixed(2)}%` : `${pct.toFixed(2)}%`
  }

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-medium text-gray-200">实盘记录</h2>
        <Link
          to="/actual-trades/new"
          className="px-3 py-1.5 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded transition-colors"
        >
          + 录入实盘
        </Link>
      </div>

      <div className="flex items-center gap-3">
        <input
          type="text"
          value={stockCodeSearch}
          onChange={(e) => setStockCodeSearch(e.target.value)}
          placeholder="股票代码"
          className="px-3 py-1.5 text-sm bg-gray-900 border border-gray-700 rounded text-gray-300 placeholder-gray-600 focus:outline-none focus:border-blue-500 w-36"
        />
        <select
          value={directionFilter}
          onChange={(e) => setDirectionFilter(e.target.value)}
          className="px-3 py-1.5 text-sm bg-gray-900 border border-gray-700 rounded text-gray-300 focus:outline-none focus:border-blue-500"
        >
          <option value="">全部方向</option>
          <option value="BUY">买入</option>
          <option value="SELL">卖出</option>
        </select>
        <button
          onClick={() => {}}
          className="px-3 py-1.5 text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 rounded border border-gray-700 transition-colors"
        >
          搜索
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
                <th className="px-4 py-3 font-medium">方向</th>
                <th className="px-4 py-3 font-medium">价格</th>
                <th className="px-4 py-3 font-medium">数量</th>
                <th className="px-4 py-3 font-medium">盈亏</th>
                <th className="px-4 py-3 font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                Array.from({ length: 3 }).map((_, i) => (
                  <tr key={i} className="border-t border-gray-800">
                    {Array.from({ length: 7 }).map((_, j) => (
                      <td key={j} className="px-4 py-3">
                        <LoadingSkeleton className="h-4 w-20" />
                      </td>
                    ))}
                  </tr>
                ))
              ) : !trades?.length ? (
                <tr>
                  <td colSpan={7}>
                    <EmptyState message="暂无实盘记录" />
                  </td>
                </tr>
              ) : (
                trades.map((trade) => (
                  <tr
                    key={trade.id}
                    className="border-t border-gray-800 hover:bg-gray-800/50 transition-colors"
                  >
                    <td className="px-4 py-3 text-gray-400">{trade.tradeDate}</td>
                    <td className="px-4 py-3 text-gray-300">
                      {trade.stockCode} {trade.stockName}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`px-2 py-0.5 text-xs rounded ${
                          trade.direction === 'BUY'
                            ? 'bg-green-950 text-green-400'
                            : 'bg-red-950 text-red-400'
                        }`}
                      >
                        {trade.direction === 'BUY' ? '买入' : '卖出'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-300">¥{trade.price.toFixed(2)}</td>
                    <td className="px-4 py-3 text-gray-400">{trade.quantity}</td>
                    <td className="px-4 py-3">
                      {trade.profitLossAmount !== undefined ? (
                        <div>
                          <span className={trade.profitLossAmount >= 0 ? 'text-green-400' : 'text-red-400'}>
                            {fmtPL(trade.profitLossAmount)}
                          </span>
                          <span className="ml-1 text-xs text-gray-500">
                            ({fmtPLPct(trade.profitLossPercent)})
                          </span>
                        </div>
                      ) : (
                        <span className="text-gray-600">已匹配</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <Link
                        to={`/actual-trades/${trade.id}/edit`}
                        className="text-xs text-blue-400 hover:text-blue-300"
                      >
                        编辑
                      </Link>
                      <button
                        onClick={() => handleDelete(trade.id)}
                        className="ml-2 text-xs text-red-400 hover:text-red-300"
                      >
                        删除
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* 持仓概览 */}
      {!isLoading && trades && trades.length > 0 && (
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
            <p className="text-xs text-gray-500 mb-1">持仓股票</p>
            <p className="text-xl font-medium text-gray-200">
              {[...new Set(trades.filter(t => t.profitLossAmount === undefined).map(t => t.stockCode))].length} 只
            </p>
          </div>
          <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
            <p className="text-xs text-gray-500 mb-1">总持仓市值</p>
            <p className="text-xl font-medium text-gray-200">—</p>
          </div>
          <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
            <p className="text-xs text-gray-500 mb-1">总浮盈</p>
            <p className="text-xl font-medium text-gray-200">—</p>
          </div>
        </div>
      )}
    </div>
  )
}
