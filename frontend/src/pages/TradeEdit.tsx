import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import ErrorAlert from '@/components/common/ErrorAlert'
import LoadingSkeleton from '@/components/common/LoadingSkeleton'
import { useActualTrades, useUpdateActualTrade } from '@/hooks'

export default function TradeEdit() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const tradeId = parseInt(id ?? '0', 10)

  const { data: trades, isLoading } = useActualTrades()
  const trade = trades?.find((t) => t.id === tradeId)
  const updateMutation = useUpdateActualTrade()

  const [form, setForm] = useState({
    stockCode: '',
    stockName: '',
    direction: 'BUY' as 'BUY' | 'SELL',
    price: '',
    quantity: '',
    tradeDate: '',
  })
  const [error, setError] = useState('')

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [_, setInit] = useState(false)

  if (!isLoading && trade && !form.stockCode) {
    setForm({
      stockCode: trade.stockCode,
      stockName: trade.stockName,
      direction: trade.direction,
      price: String(trade.price),
      quantity: String(trade.quantity),
      tradeDate: trade.tradeDate,
    })
    setInit(true)
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      await updateMutation.mutateAsync({
        id: tradeId,
        data: {
          ...form,
          price: parseFloat(form.price),
          quantity: parseInt(form.quantity),
        } as never,
      })
      navigate('/actual-trades')
    } catch (err) {
      setError((err as Error).message)
    }
  }

  if (isLoading) {
    return (
      <div className="p-6 max-w-xl">
        <LoadingSkeleton className="h-6 w-32 mb-4" />
        <LoadingSkeleton className="h-48 w-full" />
      </div>
    )
  }

  if (!trade) {
    return (
      <div className="p-6 max-w-xl">
        <ErrorAlert message="记录不存在" />
        <button onClick={() => navigate(-1)} className="mt-3 text-sm text-blue-400">返回</button>
      </div>
    )
  }

  return (
    <div className="p-6 space-y-4 max-w-xl">
      <div className="flex items-center gap-3">
        <button onClick={() => navigate(-1)} className="text-gray-400 hover:text-gray-200">←</button>
        <h2 className="text-base font-medium text-gray-200">编辑实盘</h2>
      </div>

      {error && <ErrorAlert message={error} />}

      <form onSubmit={handleSubmit} className="space-y-5 bg-gray-900 border border-gray-800 rounded-lg p-5">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs text-gray-500 mb-1">股票代码 *</label>
            <input
              value={form.stockCode}
              onChange={(e) => setForm((f) => ({ ...f, stockCode: e.target.value }))}
              className="w-full px-3 py-2 text-sm bg-gray-950 border border-gray-700 rounded text-gray-200 focus:outline-none focus:border-blue-500"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">股票名称</label>
            <input
              value={form.stockName}
              onChange={(e) => setForm((f) => ({ ...f, stockName: e.target.value }))}
              className="w-full px-3 py-2 text-sm bg-gray-950 border border-gray-700 rounded text-gray-200 focus:outline-none focus:border-blue-500"
            />
          </div>
        </div>

        <div>
          <label className="block text-xs text-gray-500 mb-2">方向 *</label>
          <div className="flex gap-4">
            {(['BUY', 'SELL'] as const).map((d) => (
              <label key={d} className="flex items-center gap-1.5 cursor-pointer">
                <input
                  type="radio"
                  name="direction"
                  checked={form.direction === d}
                  onChange={() => setForm((f) => ({ ...f, direction: d }))}
                  className="accent-blue-500"
                />
                <span className="text-sm text-gray-300">{d === 'BUY' ? '买入' : '卖出'}</span>
              </label>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs text-gray-500 mb-1">价格 *（元）</label>
            <input
              type="number"
              step="0.01"
              value={form.price}
              onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
              className="w-full px-3 py-2 text-sm bg-gray-950 border border-gray-700 rounded text-gray-200 focus:outline-none focus:border-blue-500"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">数量 *（股）</label>
            <input
              type="number"
              value={form.quantity}
              onChange={(e) => setForm((f) => ({ ...f, quantity: e.target.value }))}
              className="w-full px-3 py-2 text-sm bg-gray-950 border border-gray-700 rounded text-gray-200 focus:outline-none focus:border-blue-500"
            />
          </div>
        </div>

        <div>
          <label className="block text-xs text-gray-500 mb-1">交易日期 *</label>
          <input
            type="date"
            value={form.tradeDate}
            onChange={(e) => setForm((f) => ({ ...f, tradeDate: e.target.value }))}
            className="w-full px-3 py-2 text-sm bg-gray-950 border border-gray-700 rounded text-gray-200 focus:outline-none focus:border-blue-500"
          />
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="px-4 py-2 text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 rounded"
          >
            取消
          </button>
          <button
            type="submit"
            disabled={updateMutation.isPending}
            className="px-4 py-2 text-sm bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 text-white rounded"
          >
            {updateMutation.isPending ? '保存中…' : '保存修改'}
          </button>
        </div>
      </form>
    </div>
  )
}
