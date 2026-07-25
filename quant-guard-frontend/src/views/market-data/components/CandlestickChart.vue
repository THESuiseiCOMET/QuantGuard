<template>
  <div ref="chartRef" class="chart" data-testid="candlestick-chart"></div>
</template>

<script setup lang="ts">
/* global HTMLDivElement, ResizeObserver, document, window */
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, CandlestickChart, LineChart } from 'echarts/charts'
import {
  DataZoomComponent,
  DatasetComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  ToolboxComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ComposeOption, ECharts } from 'echarts/core'
import type { BarSeriesOption, CandlestickSeriesOption, LineSeriesOption } from 'echarts/charts'
import type {
  DataZoomComponentOption,
  DatasetComponentOption,
  GridComponentOption,
  LegendComponentOption,
  MarkLineComponentOption,
  TitleComponentOption,
  ToolboxComponentOption,
  TooltipComponentOption,
} from 'echarts/components'
import type { DailyPrice } from '@/types/marketData'

type ChartOption = ComposeOption<
  | BarSeriesOption
  | CandlestickSeriesOption
  | LineSeriesOption
  | DataZoomComponentOption
  | DatasetComponentOption
  | GridComponentOption
  | LegendComponentOption
  | MarkLineComponentOption
  | TitleComponentOption
  | ToolboxComponentOption
  | TooltipComponentOption
>

const props = defineProps<{
  prices: DailyPrice[]
}>()

const emit = defineEmits<{
  candleClick: [price: DailyPrice]
  visibleRangeChange: [range: { startIndex: number; endIndex: number; userInitiated: boolean }]
}>()

const maxInitialVisibleCandles = 240

echarts.use([
  CandlestickChart,
  BarChart,
  LineChart,
  GridComponent,
  TooltipComponent,
  DataZoomComponent,
  ToolboxComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  DatasetComponent,
  CanvasRenderer,
])

const chartRef = ref<HTMLDivElement>()
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  window.addEventListener('qg-theme-change', handleThemeChange)
  void nextTick(() => {
    initChart()
    renderChart()
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('qg-theme-change', handleThemeChange)
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})

watch(
  () => props.prices,
  () => {
    renderChart()
  },
  { deep: true },
)

function initChart() {
  if (!chartRef.value || chart) return
  chart = echarts.init(chartRef.value)
  chart.on('click', (params: unknown) => {
    const dataIndex = chartClickDataIndex(params)
    const price = Number.isInteger(dataIndex) ? props.prices[dataIndex] : undefined
    if (price) emit('candleClick', price)
  })
  chart.on('datazoom', (params: unknown) => emitVisibleRange(params))
  chart.on('restore', restoreInitialVisibleRange)
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartRef.value)
}

function renderChart() {
  if (!chartRef.value) return
  initChart()
  if (!chart) return

  if (props.prices.length === 0) {
    chart.clear()
    return
  }

  const dates = props.prices.map((item) => item.tradeDate)
  const darkMode = document.documentElement.classList.contains('dark')
  const chartTextColor = darkMode ? '#d4d4d8' : '#4b5563'
  const chartLineColor = darkMode ? '#52525b' : '#d1d5db'
  const chartSplitColor = darkMode ? '#343434' : '#e5e7eb'
  const chartAreaColors = darkMode ? ['#232323', '#292929'] : ['#ffffff', '#f8fafc']
  const initialRange = initialVisibleRange()
  const initialStart =
    props.prices.length > 1 ? (initialRange.startIndex / (props.prices.length - 1)) * 100 : 0
  const candleData = props.prices.map((item) => [
    toNumber(item.open),
    toNumber(item.close),
    toNumber(item.low),
    toNumber(item.high),
  ])
  const volumeData = props.prices.map((item) => ({
    value: toNumber(item.volumeLots),
    itemStyle: {
      color: toNumber(item.close) >= toNumber(item.open) ? '#d93025' : '#188038',
    },
  }))

  const option: ChartOption = {
    animation: false,
    color: ['#d93025', '#188038'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: darkMode ? '#262626' : '#ffffff',
      borderColor: chartLineColor,
      borderWidth: 1,
      textStyle: { color: darkMode ? '#f3f4f6' : '#172033' },
      formatter(params) {
        const rows = Array.isArray(params) ? params : [params]
        const index = rows[0]?.dataIndex ?? 0
        const price = props.prices[index]
        if (!price) return ''
        return [
          `<strong>${price.tradeDate}</strong>`,
          `开盘：${formatDecimal(price.open)}`,
          `最高：${formatDecimal(price.high)}`,
          `最低：${formatDecimal(price.low)}`,
          `收盘：${formatDecimal(price.close)}`,
          `昨收：${formatDecimal(price.previousClose)}`,
          `涨跌额：${signed(price.change)}`,
          `涨跌幅：${signed(price.percentageChange)}%`,
          `成交量：${formatDecimal(price.volumeLots)} 手`,
          `成交额：${formatDecimal(price.amountCny)} 元`,
        ].join('<br/>')
      },
    },
    toolbox: {
      right: 12,
      iconStyle: { borderColor: chartTextColor },
      feature: {
        dataZoom: { yAxisIndex: false },
        restore: {},
        saveAsImage: {},
      },
    },
    grid: [
      { left: 56, right: 76, top: 36, height: '58%' },
      { left: 56, right: 76, top: '73%', height: '16%' },
    ],
    xAxis: [
      {
        type: 'category',
        data: dates,
        boundaryGap: true,
        axisLine: { onZero: false, lineStyle: { color: chartLineColor } },
        axisLabel: { color: chartTextColor },
      },
      {
        type: 'category',
        gridIndex: 1,
        data: dates,
        boundaryGap: true,
        axisLabel: { show: false },
        axisLine: { lineStyle: { color: chartLineColor } },
      },
    ],
    yAxis: [
      {
        scale: true,
        axisLabel: { color: chartTextColor },
        splitLine: { lineStyle: { color: chartSplitColor } },
        splitArea: { show: true, areaStyle: { color: chartAreaColors } },
      },
      {
        scale: true,
        gridIndex: 1,
        splitNumber: 2,
        axisLabel: { color: chartTextColor },
        splitLine: { lineStyle: { color: chartSplitColor } },
      },
    ],
    dataZoom: [
      {
        type: 'inside',
        xAxisIndex: [0, 1],
        start: initialStart,
        end: 100,
        zoomOnMouseWheel: true,
        moveOnMouseWheel: true,
        moveOnMouseMove: true,
        preventDefaultMouseMove: true,
      },
      {
        type: 'slider',
        xAxisIndex: [0, 1],
        bottom: 6,
        height: 28,
        start: initialStart,
        end: 100,
        handleSize: '100%',
        moveHandleSize: 10,
        borderColor: chartLineColor,
        backgroundColor: darkMode ? '#292929' : '#f8fafc',
        fillerColor: darkMode ? 'rgb(82 82 91 / 45%)' : 'rgb(148 163 184 / 30%)',
        handleStyle: {
          color: darkMode ? '#d4d4d8' : '#ffffff',
          borderColor: darkMode ? '#a1a1aa' : '#64748b',
          borderWidth: 2,
        },
        moveHandleStyle: {
          color: darkMode ? '#a1a1aa' : '#64748b',
        },
        textStyle: { color: chartTextColor },
      },
    ],
    series: [
      {
        id: 'price-candles',
        name: '日 K',
        type: 'candlestick',
        data: candleData,
        itemStyle: {
          color: '#d93025',
          color0: '#188038',
          borderColor: '#d93025',
          borderColor0: '#188038',
        },
        markLine: closePriceMarkLine(props.prices.length - 1),
      },
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumeData,
      },
    ],
  }

  chart.setOption(option, true)
  emit('visibleRangeChange', { ...initialRange, userInitiated: false })
}

function handleThemeChange() {
  renderChart()
}

function toNumber(value: string | number | null | undefined) {
  const numberValue = Number(value ?? 0)
  return Number.isFinite(numberValue) ? numberValue : 0
}

function optionalNumber(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return null
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

function formatDecimal(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-'
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue.toLocaleString('zh-CN') : String(value)
}

function chartClickDataIndex(params: unknown) {
  if (!params || typeof params !== 'object' || !('dataIndex' in params)) return -1
  const value = Number((params as { dataIndex?: unknown }).dataIndex)
  return Number.isInteger(value) ? value : -1
}

function emitVisibleRange(params: unknown) {
  if (!chart || props.prices.length === 0) return
  const zoom = dataZoomRange(params)
  const lastIndex = props.prices.length - 1
  const start = Math.min(100, Math.max(0, Number(zoom?.start ?? 0)))
  const end = Math.min(100, Math.max(start, Number(zoom?.end ?? 100)))
  const startIndex = dataZoomIndex(zoom?.startValue, start, lastIndex, 'start')
  const endIndex = dataZoomIndex(zoom?.endValue, end, lastIndex, 'end')
  updateClosePriceLine(endIndex)
  emit('visibleRangeChange', { startIndex, endIndex, userInitiated: true })
}

function restoreInitialVisibleRange() {
  if (props.prices.length === 0) return
  const range = initialVisibleRange()
  updateClosePriceLine(range.endIndex)
  emit('visibleRangeChange', { ...range, userInitiated: false })
}

function initialVisibleRange() {
  const endIndex = Math.max(0, props.prices.length - 1)
  return {
    startIndex: Math.max(0, props.prices.length - maxInitialVisibleCandles),
    endIndex,
  }
}

function updateClosePriceLine(endIndex: number) {
  chart?.setOption({
    series: [
      {
        id: 'price-candles',
        markLine: closePriceMarkLine(endIndex),
      },
    ],
  })
}

function closePriceMarkLine(endIndex: number) {
  const price = props.prices[endIndex]
  const close = toNumber(price?.close)
  const reference = optionalNumber(price?.previousClose) ?? toNumber(price?.open)
  const color = close > reference ? '#d93025' : close < reference ? '#188038' : '#64748b'
  return {
    silent: true,
    symbol: ['none', 'none'],
    lineStyle: {
      color,
      type: 'dashed' as const,
      width: 1,
    },
    label: {
      show: true,
      position: 'end' as const,
      formatter: formatDecimal(close),
      color: '#ffffff',
      backgroundColor: color,
      borderRadius: 3,
      padding: [3, 6],
      fontSize: 12,
      fontWeight: 600,
    },
    data: [{ yAxis: close }],
  }
}

function dataZoomRange(params: unknown) {
  if (!params || typeof params !== 'object') return null
  const event = params as {
    start?: unknown
    end?: unknown
    startValue?: unknown
    endValue?: unknown
    batch?: unknown
  }
  if (Array.isArray(event.batch) && event.batch.length > 0) {
    return event.batch[0] as {
      start?: unknown
      end?: unknown
      startValue?: unknown
      endValue?: unknown
    }
  }
  return event
}

function dataZoomIndex(
  value: unknown,
  percentage: number,
  lastIndex: number,
  edge: 'start' | 'end',
) {
  if (typeof value === 'string') {
    const dateIndex = props.prices.findIndex((price) => price.tradeDate === value)
    if (dateIndex >= 0) return dateIndex
  }
  const numericValue = Number(value)
  if (Number.isInteger(numericValue) && numericValue >= 0 && numericValue <= lastIndex) {
    return numericValue
  }
  const approximateIndex = (percentage / 100) * lastIndex
  return edge === 'start' ? Math.floor(approximateIndex) : Math.ceil(approximateIndex)
}

function signed(value: string | number | null | undefined) {
  const numberValue = Number(value ?? 0)
  if (!Number.isFinite(numberValue)) return String(value ?? '-')
  return `${numberValue > 0 ? '+' : ''}${numberValue.toLocaleString('zh-CN')}`
}
</script>

<style scoped>
.chart {
  width: 100%;
  height: 520px;
  min-height: 360px;
}
</style>
