import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'

interface TrendPoint {
  date: string
  planCumulative: number
  actualCumulative: number
}

interface Props {
  data: TrendPoint[]
  baselineCapital: number
}

export default function TrendChart({ data, baselineCapital }: Props) {
  const chartRef = useRef<HTMLDivElement>(null)
  const instanceRef = useRef<ECharts | null>(null)

  useEffect(() => {
    if (!chartRef.current) return
    instanceRef.current = echarts.init(chartRef.current)
    return () => instanceRef.current?.dispose()
  }, [])

  useEffect(() => {
    const instance = instanceRef.current
    if (!instance || !data.length) return

    const dates = data.map((d) => d.date)
    const planSeries = data.map((d) => [d.date, d.planCumulative])
    const actualSeries = data.map((d) => [d.date, d.actualCumulative])

    const option: echarts.EChartsOption = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        formatter: (params: unknown) => {
          const arr = params as { axisValue: string; data: [string, number]; seriesName: string; color: string }[]
          const date = arr[0]?.axisValue ?? ''
          let html = `<b>${date}</b><br/>`
          arr.forEach((p) => {
            const val = p.data?.[1]?.toFixed(2) ?? '-'
            html += `<span style="display:inline-block;margin-right:4px;border-radius:50%;width:10px;height:10px;background-color:${p.color};"></span>${p.seriesName}: <b>${val}%</b><br/>`
          })
          const planVal = arr.find((p) => p.seriesName === '预案累计收益')?.data?.[1] ?? 0
          const actualVal = arr.find((p) => p.seriesName === '实盘累计收益')?.data?.[1] ?? 0
          const gap = (planVal - actualVal).toFixed(2)
          const color = parseFloat(gap) >= 0 ? '#22C55E' : '#EF4444'
          html += `<span style="color:${color}">差值: ${parseFloat(gap) >= 0 ? '+' : ''}${gap}%</span>`
          return html
        },
      },
      legend: {
        data: ['预案累计收益', '实盘累计收益'],
        bottom: 0,
        textStyle: { color: '#9CA3AF', fontSize: 12 },
      },
      grid: { left: 50, right: 20, top: 20, bottom: 50 },
      xAxis: {
        type: 'category',
        data: dates,
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: {
          color: '#6B7280',
          fontSize: 11,
          formatter: (v: string) => v.slice(5),
        },
      },
      yAxis: {
        type: 'value',
        axisLabel: { formatter: '{value}%', color: '#6B7280', fontSize: 11 },
        splitLine: { lineStyle: { color: '#1F2937', type: 'dashed' } },
      },
      dataZoom: [
        { type: 'inside', start: 0, end: 100 },
        { type: 'slider', start: 0, end: 100, height: 20, bottom: 25, borderColor: '#374151', fillerColor: 'rgba(59,130,246,0.2)', handleStyle: { color: '#3B82F6' }, textStyle: { color: '#6B7280' } },
      ],
      series: [
        {
          name: '预案累计收益',
          type: 'line',
          smooth: true,
          itemStyle: { color: '#5470C6' },
          lineStyle: { width: 2 },
          data: planSeries,
          symbol: 'circle',
          symbolSize: 4,
        },
        {
          name: '实盘累计收益',
          type: 'line',
          smooth: true,
          itemStyle: { color: '#FE5C3A' },
          lineStyle: { width: 2 },
          data: actualSeries,
          symbol: 'circle',
          symbolSize: 4,
        },
      ],
    }

    instance.setOption(option)
  }, [data])

  return <div ref={chartRef} className="w-full h-64" />
}
