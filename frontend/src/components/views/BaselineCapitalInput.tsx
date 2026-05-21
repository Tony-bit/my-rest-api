import { useState, useEffect } from 'react'
import { useSystemConfig, useUpdateBaselineCapital } from '@/hooks'

interface Props {
  planCashBalance?: number
  actualCashBalance?: number
  readonly?: boolean
}

export default function BaselineCapitalInput({ planCashBalance, actualCashBalance, readonly = false }: Props) {
  const { data: config, isLoading } = useSystemConfig()
  const updateMutation = useUpdateBaselineCapital()

  const [value, setValue] = useState('')
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    if (config?.baselineCapital !== undefined) {
      setValue(String(config.baselineCapital))
    }
  }, [config])

  const currentBaseline = config?.baselineCapital ?? 0
  const inputNumber = parseInt(value.replace(/,/g, ''), 10) || 0
  const diff = inputNumber - currentBaseline
  const isRaise = diff > 0
  const isLower = diff < 0
  const cash = (actualCashBalance ?? planCashBalance) ?? 0
  const canLower = isLower && inputNumber <= cash

  const hint =
    isLoading || inputNumber <= 0 || inputNumber === currentBaseline
      ? ''
      : isRaise
      ? `提高到 ¥${formatNumber(inputNumber)} → 预案+实盘现金同步充值 ${formatNumber(diff)}`
      : isLower
      ? canLower
        ? `降低到 ¥${formatNumber(inputNumber)} → 预案+实盘现金同步回拨 ${formatNumber(Math.abs(diff))}`
        : `⚠️ 不可行，当前现金余额 ${formatNumber(cash)} 元不足`
      : ''

  const hintType = isLower && !canLower ? 'error' : isRaise || isLower ? 'info' : 'neutral'

  const handleSave = async () => {
    if (inputNumber <= 0) return
    if (isLower && !canLower) return
    try {
      await updateMutation.mutateAsync(inputNumber)
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } catch {
      // error handled by mutation
    }
  }

  if (isLoading) {
    return <div className="h-10 bg-gray-800 rounded animate-pulse w-64" />
  }

  return (
    <div className="space-y-1.5">
      <div className="flex items-center gap-2">
        <span className="text-sm text-gray-400 whitespace-nowrap">基准资金（元）</span>
        <input
          type="text"
          value={value}
          onChange={(e) => {
            setValue(e.target.value.replace(/[^\d]/g, ''))
            setSaved(false)
          }}
          readOnly={readonly}
          className="w-44 px-3 py-1.5 text-sm bg-gray-900 border border-gray-700 rounded text-gray-100 focus:outline-none focus:border-blue-500 disabled:opacity-50"
          placeholder="请输入金额"
        />
        {!readonly && (
          <button
            onClick={handleSave}
            disabled={
              inputNumber <= 0 ||
              inputNumber === currentBaseline ||
              (isLower && !canLower) ||
              updateMutation.isPending
            }
            className="px-3 py-1.5 text-sm bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white rounded transition-colors"
          >
            {updateMutation.isPending ? '保存中…' : '保存'}
          </button>
        )}
      </div>
      {hint && (
        <p
          className={`text-xs ${
            hintType === 'error'
              ? 'text-red-400'
              : hintType === 'info'
              ? 'text-blue-400'
              : 'text-gray-500'
          }`}
        >
          {hint}
        </p>
      )}
      {saved && <p className="text-xs text-green-400">保存成功</p>}
    </div>
  )
}

function formatNumber(n: number) {
  return n.toLocaleString('zh-CN')
}
