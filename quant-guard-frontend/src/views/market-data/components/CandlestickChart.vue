<template>
  <div ref="chartRef" class="chart"></div>
</template>

<script setup lang="ts">
/* global HTMLDivElement, ResizeObserver */
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, CandlestickChart, LineChart } from 'echarts/charts'
import {
  DataZoomComponent,
  DatasetComponent,
  GridComponent,
  LegendComponent,
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
  | TitleComponentOption
  | ToolboxComponentOption
  | TooltipComponentOption
>

const props = defineProps<{
  prices: DailyPrice[]
}>()

echarts.use([
  CandlestickChart,
  BarChart,
  LineChart,
  GridComponent,
  TooltipComponent,
  DataZoomComponent,
  ToolboxComponent,
  LegendComponent,
  TitleComponent,
  DatasetComponent,
  CanvasRenderer,
])

const chartRef = ref<HTMLDivElement>()
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  void nextTick(() => {
    initChart()
    renderChart()
  })
})

onBeforeUnmount(() => {
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
      borderWidth: 1,
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
      feature: {
        dataZoom: { yAxisIndex: false },
        restore: {},
        saveAsImage: {},
      },
    },
    grid: [
      { left: 56, right: 28, top: 36, height: '58%' },
      { left: 56, right: 28, top: '73%', height: '16%' },
    ],
    xAxis: [
      { type: 'category', data: dates, boundaryGap: true, axisLine: { onZero: false } },
      {
        type: 'category',
        gridIndex: 1,
        data: dates,
        boundaryGap: true,
        axisLabel: { show: false },
      },
    ],
    yAxis: [
      { scale: true, splitArea: { show: true } },
      { scale: true, gridIndex: 1, splitNumber: 2 },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1], start: 55, end: 100 },
      { type: 'slider', xAxisIndex: [0, 1], top: '91%', start: 55, end: 100 },
    ],
    series: [
      {
        name: '日 K',
        type: 'candlestick',
        data: candleData,
        itemStyle: {
          color: '#d93025',
          color0: '#188038',
          borderColor: '#d93025',
          borderColor0: '#188038',
        },
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
}

function toNumber(value: string | number | null | undefined) {
  const numberValue = Number(value ?? 0)
  return Number.isFinite(numberValue) ? numberValue : 0
}

function formatDecimal(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-'
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue.toLocaleString('zh-CN') : String(value)
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
