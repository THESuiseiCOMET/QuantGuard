<template>
  <el-dialog
    v-model="visible"
    :title="`${symbol} 回本计算`"
    width="min(620px, calc(100vw - 32px))"
    destroy-on-close
  >
    <div class="position-baseline">
      <div>
        <span>当前数量</span>
        <strong>{{ formatNumber(quantity) }}</strong>
      </div>
      <div>
        <span>平均成本</span>
        <strong>{{ formatNumber(averageCost, 4) }}</strong>
      </div>
      <div>
        <span>当前价格</span>
        <el-input-number
          v-model="editableCurrentPrice"
          aria-label="当前价格"
          :min="0.0001"
          :precision="4"
          :step="0.01"
          controls-position="right"
        />
      </div>
    </div>

    <div class="position-status" :class="positionStatus.tone">
      {{ positionStatus.message }}
    </div>

    <el-segmented v-model="mode" class="mode-control" :options="modeOptions" block />

    <el-form label-position="top" class="calculator-form">
      <el-form-item label="计划补仓价格">
        <el-input-number
          v-model="additionalPrice"
          :min="0.0001"
          :precision="4"
          :step="0.01"
          controls-position="right"
        />
      </el-form-item>

      <el-form-item v-if="mode === 'QUANTITY'" label="计划补仓数量">
        <el-input-number
          v-model="additionalQuantity"
          :min="lotSize"
          :step="lotSize"
          :precision="quantityPrecision"
          controls-position="right"
        />
      </el-form-item>

      <el-form-item v-else label="希望最多上涨多少回本">
        <el-input-number
          v-model="targetGainPercent"
          :min="0"
          :max="1000"
          :precision="2"
          :step="1"
          controls-position="right"
        >
          <template #suffix>%</template>
        </el-input-number>
      </el-form-item>
    </el-form>

    <el-alert v-if="calculationError" :title="calculationError" type="warning" :closable="false" />

    <div v-else-if="activeCalculation" class="calculator-result">
      <div>
        <span>{{ mode === 'TARGET' ? '至少补仓' : '补仓数量' }}</span>
        <strong>{{ formatNumber(activeCalculation.additionalQuantity) }}</strong>
      </div>
      <div>
        <span>补仓金额</span>
        <strong>{{ formatMoney(activeCalculation.additionalAmount) }}</strong>
      </div>
      <div>
        <span>新平均成本</span>
        <strong>{{ formatNumber(activeCalculation.averageCost, 4) }}</strong>
      </div>
      <div>
        <span>回本还需上涨</span>
        <strong>{{ formatPercent(activeCalculation.breakEvenGainPercent) }}</strong>
      </div>
    </div>

    <div class="calculator-footnote">
      <span>最小交易单位 {{ formatNumber(lotSize) }}</span>
      <span>未计手续费</span>
    </div>

    <template #footer>
      <el-button type="primary" @click="visible = false">完成</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  calculatePositionRecoveryStatus,
  calculateRecoveryByQuantity,
  calculateRecoveryByTarget,
} from '@/utils/recoveryCalculator'

type CalculatorMode = 'QUANTITY' | 'TARGET'

const props = defineProps<{
  modelValue: boolean
  symbol: string
  quantity: number
  averageCost: number
  currentPrice: number
  lotSize: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const modeOptions = [
  { label: '按补仓数量', value: 'QUANTITY' },
  { label: '按目标涨幅', value: 'TARGET' },
]

const mode = ref<CalculatorMode>('QUANTITY')
const editableCurrentPrice = ref(props.currentPrice)
const additionalPrice = ref(props.currentPrice)
const additionalQuantity = ref(props.lotSize)
const targetGainPercent = ref(5)

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const quantityPrecision = computed(() => (Number.isInteger(props.lotSize) ? 0 : 4))

const positionStatus = computed(() => {
  const status = calculatePositionRecoveryStatus(
    props.quantity,
    props.averageCost,
    editableCurrentPrice.value,
  )
  if (status.state === 'INVALID') {
    return { tone: 'neutral', message: '请输入有效的当前价格后继续测算。' }
  }
  if (status.state === 'BALANCED') {
    return {
      tone: 'balanced',
      message: '当前处于盈亏平衡，可继续测算不同补仓方案。',
    }
  }
  if (status.state === 'PROFITABLE') {
    return {
      tone: 'profitable',
      message: '当前已经回本并处于盈利状态，以下结果仅用于持仓成本测算。',
    }
  }

  return {
    tone: 'losing',
    message: `当前亏损 ${formatMoney(Math.abs(status.profitAmount))}（${formatPercent(Math.abs(status.profitPercent))}），回本需要上涨 ${formatPercent(status.breakEvenGainPercent)}。`,
  }
})

const quantityCalculation = computed(() =>
  calculateRecoveryByQuantity({
    quantity: props.quantity,
    averageCost: props.averageCost,
    currentPrice: editableCurrentPrice.value,
    additionalQuantity: additionalQuantity.value,
    additionalPrice: additionalPrice.value,
  }),
)

const targetCalculation = computed(() =>
  calculateRecoveryByTarget({
    quantity: props.quantity,
    averageCost: props.averageCost,
    currentPrice: editableCurrentPrice.value,
    additionalPrice: additionalPrice.value,
    targetGainPercent: targetGainPercent.value,
    lotSize: props.lotSize,
  }),
)

const activeCalculation = computed(() =>
  mode.value === 'QUANTITY' ? quantityCalculation.value : targetCalculation.value,
)

const calculationError = computed(() => {
  if (activeCalculation.value) return ''
  if (mode.value === 'TARGET') {
    return '当前补仓价格过高，无法把平均成本降到目标回本价格以内。'
  }
  return '请输入有效的补仓价格和数量。'
})

watch(
  () => props.modelValue,
  (value) => {
    if (!value) return
    mode.value = 'QUANTITY'
    editableCurrentPrice.value = props.currentPrice
    additionalPrice.value = props.currentPrice
    additionalQuantity.value = props.lotSize
    targetGainPercent.value = 5
  },
)

function formatNumber(value: number, maximumFractionDigits = 2) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits,
  }).format(value)
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatPercent(value: number) {
  return `${formatNumber(Math.max(0, value), 2)}%`
}
</script>

<style scoped>
.position-baseline,
.calculator-result {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
}

.position-baseline div,
.calculator-result div {
  display: grid;
  min-width: 0;
  gap: 5px;
  padding: 12px 14px;
  border-right: 1px solid var(--qg-border);
}

.position-baseline div:last-child,
.calculator-result div:last-child {
  border-right: 0;
}

.position-baseline span,
.calculator-result span,
.calculator-footnote {
  color: var(--qg-text-muted);
  font-size: 12px;
}

.position-baseline strong,
.calculator-result strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 17px;
}

.position-baseline :deep(.el-input-number) {
  width: 100%;
}

.position-status {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
  background: var(--qg-subtle);
  color: var(--qg-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.position-status.losing {
  border-color: rgb(22 143 91 / 35%);
  color: #168f5b;
}

.position-status.profitable {
  border-color: rgb(209 67 67 / 35%);
  color: #d14343;
}

:global(html.dark) .position-status.losing {
  color: #4ade80;
}

:global(html.dark) .position-status.profitable {
  color: #f87171;
}

.mode-control {
  width: 100%;
  margin: 16px 0;
}

.calculator-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.calculator-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.calculator-form :deep(.el-input-number) {
  width: 100%;
}

.calculator-result {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 4px;
}

.calculator-footnote {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

@media (max-width: 620px) {
  .position-baseline,
  .calculator-result,
  .calculator-form {
    grid-template-columns: 1fr 1fr;
  }

  .position-baseline div:nth-child(2),
  .calculator-result div:nth-child(2n) {
    border-right: 0;
  }

  .position-baseline div:last-child {
    grid-column: 1 / -1;
    border-top: 1px solid var(--qg-border);
  }
}
</style>
