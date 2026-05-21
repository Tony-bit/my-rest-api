import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import ErrorAlert from '@/components/common/ErrorAlert'
import LoadingSkeleton from '@/components/common/LoadingSkeleton'
import { usePlan, useUpdatePlan } from '@/hooks'
import type { Cycle, ConditionType, Direction } from '@/types'

const MA_PERIODS = [5, 10, 20, 60, 120, 250]
const CYCLE_OPTIONS: { value: Cycle; label: string }[] = [
  { value: 'DAILY', label: '日度' },
  { value: 'WEEKLY', label: '周度' },
  { value: 'MONTHLY', label: '月度' },
]

export default function PlanEdit() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const planId = parseInt(id ?? '0', 10)

  const { data: plan, isLoading } = usePlan(planId)
  const updateMutation = useUpdatePlan()

  const [form, setForm] = useState({
    name: '',
    stockCode: '',
    stockName: '',
    cycle: 'WEEKLY' as Cycle,
    validUntil: '',
    executionQuantity: 100,
  })
  const [buyType, setBuyType] = useState<ConditionType>('MA')
  const [buyMaPeriod, setBuyMaPeriod] = useState(20)
  const [buyPrice, setBuyPrice] = useState('')
  const [sellType, setSellType] = useState<ConditionType>('MA')
  const [sellMaPeriod, setSellMaPeriod] = useState(10)
  const [sellPrice, setSellPrice] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (!plan) return
    setForm({
      name: plan.name,
      stockCode: plan.stockCode,
      stockName: plan.stockName,
      cycle: plan.cycle,
      validUntil: plan.validUntil,
      executionQuantity: plan.executionQuantity,
    })
    const buyCond = plan.conditions.find((c) => c.direction === 'BUY')
    const sellCond = plan.conditions.find((c) => c.direction === 'SELL')
    if (buyCond) {
      setBuyType(buyCond.conditionType)
      if (buyCond.conditionType === 'MA') setBuyMaPeriod(buyCond.maPeriod ?? 20)
      else setBuyPrice(String(buyCond.targetPrice ?? ''))
    }
    if (sellCond) {
      setSellType(sellCond.conditionType)
      if (sellCond.conditionType === 'MA') setSellMaPeriod(sellCond.maPeriod ?? 10)
      else setSellPrice(String(sellCond.targetPrice ?? ''))
    }
  }, [plan])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (!form.name || !form.stockCode || !form.validUntil) {
      setError('请填写必填项')
      return
    }
    try {
      await updateMutation.mutateAsync({ id: planId, data: form })
      navigate(`/plans/${planId}`)
    } catch (err) {
      setError((err as Error).message)
    }
  }

  if (isLoading) {
    return (
      <div className="p-6 max-w-2xl">
        <LoadingSkeleton className="h-6 w-32 mb-4" />
        <LoadingSkeleton className="h-64 w-full" />
      </div>
    )
  }

  if (plan?.status !== 'PENDING') {
    return (
      <div className="p-6 max-w-2xl">
        <ErrorAlert message="该预案当前状态不允许编辑" />
        <button onClick={() => navigate(-1)} className="mt-3 text-sm text-blue-400">返回</button>
      </div>
    )
  }

  return (
    <div className="p-6 space-y-4 max-w-2xl">
      <div className="flex items-center gap-3">
        <button onClick={() => navigate(-1)} className="text-gray-400 hover:text-gray-200">←</button>
        <h2 className="text-base font-medium text-gray-200">编辑预案</h2>
      </div>

      {error && <ErrorAlert message={error} />}

      <form onSubmit={handleSubmit} className="space-y-5 bg-gray-900 border border-gray-800 rounded-lg p-5">
        <section className="space-y-3">
          <h3 className="text-sm font-medium text-gray-300">基本信息</h3>
          <Field label="预案名称 *">
            <input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} className="field-input" />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="股票代码 *">
              <input value={form.stockCode} onChange={(e) => setForm((f) => ({ ...f, stockCode: e.target.value }))} className="field-input" />
            </Field>
            <Field label="股票名称">
              <input value={form.stockName} onChange={(e) => setForm((f) => ({ ...f, stockName: e.target.value }))} className="field-input" />
            </Field>
          </div>
          <Field label="周期">
            <div className="flex gap-4">
              {CYCLE_OPTIONS.map((c) => (
                <label key={c.value} className="flex items-center gap-1.5 cursor-pointer">
                  <input type="radio" name="cycle" value={c.value} checked={form.cycle === c.value} onChange={() => setForm((f) => ({ ...f, cycle: c.value }))} className="accent-blue-500" />
                  <span className="text-sm text-gray-300">{c.label}</span>
                </label>
              ))}
            </div>
          </Field>
          <Field label="有效期至 *">
            <input type="date" value={form.validUntil} onChange={(e) => setForm((f) => ({ ...f, validUntil: e.target.value }))} className="field-input" />
          </Field>
          <Field label="股数">
            <input type="number" value={form.executionQuantity} onChange={(e) => setForm((f) => ({ ...f, executionQuantity: parseInt(e.target.value) || 0 }))} className="field-input w-32" min="1" />
          </Field>
        </section>

        <div className="flex justify-end gap-3 pt-2">
          <button type="button" onClick={() => navigate(-1)} className="px-4 py-2 text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 rounded">取消</button>
          <button type="submit" disabled={updateMutation.isPending} className="px-4 py-2 text-sm bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 text-white rounded">
            {updateMutation.isPending ? '保存中…' : '保存修改'}
          </button>
        </div>
      </form>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs text-gray-500 mb-1">{label}</label>
      {children}
    </div>
  )
}
