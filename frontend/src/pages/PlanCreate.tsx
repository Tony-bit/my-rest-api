import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import ErrorAlert from '@/components/common/ErrorAlert'
import { useCreatePlan, usePlans } from '@/hooks'
import type { Cycle, ConditionType } from '@/types'

const CYCLE_OPTIONS: { value: Cycle; label: string }[] = [
  { value: 'DAILY', label: '日度' },
  { value: 'WEEKLY', label: '周度' },
  { value: 'MONTHLY', label: '月度' },
]

const MA_PERIODS = [5, 10, 20, 60, 120, 250]

export default function PlanCreate() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const createMutation = useCreatePlan()
  
  // Check if this is creating a SELL plan (linked to a BUY plan)
  const buyPlanIdParam = searchParams.get('buyPlanId')
  const isSellPlan = buyPlanIdParam !== null

  // Fetch buy plan details if creating SELL plan
  const { data: buyPlan } = usePlans()
  const linkedBuyPlan = isSellPlan && buyPlanIdParam
    ? buyPlan?.find(p => p.id === parseInt(buyPlanIdParam))
    : undefined

  const [form, setForm] = useState({
    name: '',
    stockCode: linkedBuyPlan?.stockCode || '',
    stockName: linkedBuyPlan?.stockName || '',
    cycle: (linkedBuyPlan?.cycle as Cycle) || ('WEEKLY' as Cycle),
    validUntil: '',
    executionQuantity: linkedBuyPlan?.executionQuantity || 100,
  })
  const planType = isSellPlan ? 'SELL' : 'BUY'
  const [conditionType, setConditionType] = useState<ConditionType>('MA')
  const [maPeriod, setMaPeriod] = useState<number>(planType === 'BUY' ? 20 : 10)
  const [targetPrice, setTargetPrice] = useState('')
  const [error, setError] = useState('')

  const setCycleDefault = (cycle: Cycle) => {
    setForm((f) => ({ ...f, cycle }))
    if (!form.validUntil) {
      const d = new Date()
      if (cycle === 'DAILY') d.setDate(d.getDate() + 30)
      else if (cycle === 'WEEKLY') d.setDate(d.getDate() + 30)
      else d.setMonth(d.getMonth() + 1, 0)
      setForm((f) => ({ ...f, validUntil: d.toISOString().slice(0, 10) }))
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (!form.name || !form.stockCode || !form.validUntil) {
      setError('请填写必填项')
      return
    }

    try {
      let condition: { conditionType: 'MA'; maPeriod: number } | { conditionType: 'PRICE'; targetPrice: number }
      if (conditionType === 'MA') {
        condition = { conditionType: 'MA', maPeriod }
      } else {
        condition = { conditionType: 'PRICE', targetPrice: parseFloat(targetPrice) }
      }

      if (planType === 'BUY') {
        const result = await createMutation.mutateAsync({
          name: form.name,
          stockCode: form.stockCode,
          stockName: form.stockName,
          cycle: form.cycle,
          validUntil: form.validUntil,
          planType: 'BUY',
          executionQuantity: form.executionQuantity,
          condition,
        })
        navigate(`/plans/${result.id}`)
      } else {
        // Creating SELL plan - need to use sell-specific API
        const { planApi } = await import('@/api')
        const result = await planApi.createSellPlan({
          buyPlanId: parseInt(buyPlanIdParam!),
          name: form.name,
          cycle: form.cycle,
          validUntil: form.validUntil,
          condition,
        })
        navigate(`/plans/${result.id}`)
      }
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <div className="p-6 space-y-4 max-w-2xl">
      <div className="flex items-center gap-3">
        <button onClick={() => navigate(-1)} className="text-gray-400 hover:text-gray-200">←</button>
        <h2 className="text-base font-medium text-gray-200">
          {planType === 'BUY' ? '创建买入预案' : '创建卖出预案'}
        </h2>
      </div>

      {error && <ErrorAlert message={error} />}

      <form onSubmit={handleSubmit} className="space-y-5 bg-gray-900 border border-gray-800 rounded-lg p-5">
        {/* 关联买入预案提示 (SELL plan only) */}
        {isSellPlan && linkedBuyPlan && (
          <div className="p-3 bg-orange-900/30 border border-orange-800 rounded">
            <p className="text-sm text-orange-300">
              关联买入预案：<span className="font-medium">{linkedBuyPlan.stockCode} {linkedBuyPlan.stockName}</span>
              <span className="ml-2 text-orange-400">持股中</span>
            </p>
          </div>
        )}

        {/* 基本信息 */}
        <section className="space-y-3">
          <h3 className="text-sm font-medium text-gray-300">基本信息</h3>
          <Field label="预案名称 *">
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              className="field-input"
              placeholder={planType === 'BUY' ? '如：茅台 MA20 买入' : '如：茅台 MA10 卖出'}
            />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="股票代码 *">
              <input
                value={form.stockCode}
                onChange={(e) => setForm((f) => ({ ...f, stockCode: e.target.value }))}
                className="field-input"
                placeholder="如：600519"
                disabled={isSellPlan}
              />
            </Field>
            <Field label="股票名称">
              <input
                value={form.stockName}
                onChange={(e) => setForm((f) => ({ ...f, stockName: e.target.value }))}
                className="field-input"
                placeholder="如：贵州茅台"
                disabled={isSellPlan}
              />
            </Field>
          </div>
          <Field label="周期 *">
            <div className="flex gap-4">
              {CYCLE_OPTIONS.map((c) => (
                <label key={c.value} className="flex items-center gap-1.5 cursor-pointer">
                  <input
                    type="radio"
                    name="cycle"
                    value={c.value}
                    checked={form.cycle === c.value}
                    onChange={() => setCycleDefault(c.value)}
                    className="accent-blue-500"
                  />
                  <span className="text-sm text-gray-300">{c.label}</span>
                </label>
              ))}
            </div>
          </Field>
          <Field label="有效期至 *">
            <input
              type="date"
              value={form.validUntil}
              onChange={(e) => setForm((f) => ({ ...f, validUntil: e.target.value }))}
              className="field-input"
            />
          </Field>
          
          {/* 买入预案显示股数，卖出预案不显示 */}
          {planType === 'BUY' && (
            <Field label="买入股数">
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  value={form.executionQuantity}
                  onChange={(e) => setForm((f) => ({ ...f, executionQuantity: parseInt(e.target.value) || 0 }))}
                  className="field-input w-32"
                  min="1"
                />
                <span className="text-sm text-gray-500">股（全仓）</span>
              </div>
            </Field>
          )}
          {planType === 'SELL' && (
            <div className="p-3 bg-gray-950 border border-gray-800 rounded">
              <p className="text-sm text-gray-400">
                卖出数量：<span className="font-medium text-gray-200">{linkedBuyPlan?.executionQuantity || form.executionQuantity}</span> 股
                <span className="ml-2 text-xs text-gray-500">（系统锁定，与买入数量一致）</span>
              </p>
            </div>
          )}
        </section>

        {/* 触发条件 */}
        <section className="space-y-3 pt-3 border-t border-gray-800">
          <h3 className="text-sm font-medium text-gray-300">
            {planType === 'BUY' ? '买入条件' : '卖出条件'}
          </h3>
          <div className="flex gap-4">
            <label className="flex items-center gap-1.5 cursor-pointer">
              <input
                type="radio"
                name="conditionType"
                checked={conditionType === 'MA'}
                onChange={() => setConditionType('MA')}
                className="accent-blue-500"
              />
              <span className="text-sm text-gray-300">MA均线</span>
            </label>
            <label className="flex items-center gap-1.5 cursor-pointer">
              <input
                type="radio"
                name="conditionType"
                checked={conditionType === 'PRICE'}
                onChange={() => setConditionType('PRICE')}
                className="accent-blue-500"
              />
              <span className="text-sm text-gray-300">固定价格</span>
            </label>
          </div>
          
          {conditionType === 'MA' ? (
            <select
              value={maPeriod}
              onChange={(e) => setMaPeriod(parseInt(e.target.value))}
              className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded text-gray-300"
            >
              {MA_PERIODS.map((p) => (
                <option key={p} value={p}>MA{p}</option>
              ))}
            </select>
          ) : (
            <input
              type="number"
              value={targetPrice}
              onChange={(e) => setTargetPrice(e.target.value)}
              placeholder="目标价格"
              className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded text-gray-300"
              step="0.01"
            />
          )}
          <p className="text-xs text-gray-600">触碰容差固定 0.3%，不可调节</p>
        </section>

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="px-4 py-2 text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 rounded transition-colors"
          >
            取消
          </button>
          <button
            type="submit"
            disabled={createMutation.isPending}
            className="px-4 py-2 text-sm bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 text-white rounded transition-colors"
          >
            {createMutation.isPending ? '创建中…' : (planType === 'BUY' ? '创建买入预案' : '创建卖出预案')}
          </button>
        </div>
      </form>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs text-gray-500 mb-1">
        {label}
      </label>
      {children}
    </div>
  )
}
