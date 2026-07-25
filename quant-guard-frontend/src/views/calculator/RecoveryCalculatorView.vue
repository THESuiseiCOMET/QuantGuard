<template>
  <section class="page">
    <PageHeader title="回本计算" />

    <div class="calculator-workspace">
      <section class="surface calculator-panel">
        <header class="panel-header">
          <h2>持仓参数</h2>
          <el-button @click="resetCalculator">重置</el-button>
        </header>

        <div class="input-grid">
          <label>
            <span>持仓数量</span>
            <el-input-number
              v-model="quantity"
              aria-label="持仓数量"
              :min="0.0001"
              :precision="quantityPrecision"
              :step="lotSize"
              controls-position="right"
            />
          </label>
          <label>
            <span>平均成本</span>
            <el-input-number
              v-model="averageCost"
              aria-label="平均成本"
              :min="0.0001"
              :precision="4"
              :step="0.01"
              controls-position="right"
            />
          </label>
          <label>
            <span>当前价格</span>
            <el-input-number
              v-model="currentPrice"
              aria-label="当前价格"
              :min="0.0001"
              :precision="4"
              :step="0.01"
              controls-position="right"
            />
          </label>
          <label>
            <span>最小交易单位</span>
            <el-input-number
              v-model="lotSize"
              aria-label="最小交易单位"
              :min="0.0001"
              :precision="quantityPrecision"
              :step="1"
              controls-position="right"
            />
          </label>
        </div>

        <div class="position-status" :class="positionStatus.tone">
          <span>{{ positionStatus.message }}</span>
          <strong v-if="positionStatus.amount">{{ positionStatus.amount }}</strong>
        </div>

        <el-segmented v-model="mode" class="mode-control" :options="modeOptions" block />

        <div class="plan-grid">
          <label>
            <span>补仓价格</span>
            <el-input-number
              v-model="additionalPrice"
              aria-label="补仓价格"
              :min="0.0001"
              :precision="4"
              :step="0.01"
              controls-position="right"
            />
          </label>
          <label v-if="mode === 'QUANTITY'">
            <span>补仓数量</span>
            <el-input-number
              v-model="additionalQuantity"
              aria-label="补仓数量"
              :min="0"
              :precision="quantityPrecision"
              :step="lotSize"
              controls-position="right"
            />
          </label>
          <label v-else>
            <span>目标回本涨幅</span>
            <el-input-number
              v-model="targetGainPercent"
              aria-label="目标回本涨幅"
              :min="0"
              :max="1000"
              :precision="2"
              :step="1"
              controls-position="right"
            >
              <template #suffix>%</template>
            </el-input-number>
          </label>
        </div>
      </section>

      <section class="surface result-panel">
        <header class="panel-header">
          <h2>测算结果</h2>
          <span>{{ mode === 'QUANTITY' ? '按补仓数量' : '按目标涨幅' }}</span>
        </header>

        <div v-if="calculationError" class="calculation-error">
          {{ calculationError }}
        </div>

        <div v-else-if="activeCalculation" class="result-grid">
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
          <div>
            <span>补仓后数量</span>
            <strong>{{ formatNumber(activeCalculation.totalQuantity) }}</strong>
          </div>
          <div>
            <span>补仓后成本</span>
            <strong>{{ formatMoney(activeCalculation.totalCost) }}</strong>
          </div>
        </div>

        <footer class="result-footnote">
          <span>最小交易单位 {{ formatNumber(lotSize) }}</span>
          <span>未计手续费</span>
        </footer>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  calculatePositionRecoveryStatus,
  calculateRecoveryByQuantity,
  calculateRecoveryByTarget,
} from '@/utils/recoveryCalculator'

type CalculatorMode = 'QUANTITY' | 'TARGET'

const defaults = {
  quantity: 1000,
  averageCost: 40,
  currentPrice: 35,
  lotSize: 100,
  additionalQuantity: 100,
  targetGainPercent: 5,
}

const modeOptions = [
  { label: '按补仓数量', value: 'QUANTITY' },
  { label: '按目标涨幅', value: 'TARGET' },
]

const mode = ref<CalculatorMode>('QUANTITY')
const quantity = ref(defaults.quantity)
const averageCost = ref(defaults.averageCost)
const currentPrice = ref(defaults.currentPrice)
const lotSize = ref(defaults.lotSize)
const additionalPrice = ref(defaults.currentPrice)
const additionalQuantity = ref(defaults.additionalQuantity)
const targetGainPercent = ref(defaults.targetGainPercent)

const quantityPrecision = computed(() => (Number.isInteger(lotSize.value) ? 0 : 4))
const status = computed(() =>
  calculatePositionRecoveryStatus(quantity.value, averageCost.value, currentPrice.value),
)
const positionStatus = computed(() => {
  if (status.value.state === 'INVALID') {
    return { tone: 'neutral', message: '请输入有效的持仓参数。', amount: '' }
  }
  if (status.value.state === 'BALANCED') {
    return {
      tone: 'balanced',
      message: '当前处于盈亏平衡，可继续测算不同补仓方案。',
      amount: '0.00',
    }
  }
  if (status.value.state === 'PROFITABLE') {
    return {
      tone: 'profitable',
      message: '当前已经回本并处于盈利状态，以下结果仅用于持仓成本测算。',
      amount: `+${formatMoney(status.value.profitAmount)}`,
    }
  }
  return {
    tone: 'losing',
    message: `当前亏损 ${formatPercent(Math.abs(status.value.profitPercent))}，回本需要上涨 ${formatPercent(status.value.breakEvenGainPercent)}。`,
    amount: `-${formatMoney(Math.abs(status.value.profitAmount))}`,
  }
})

const quantityCalculation = computed(() =>
  calculateRecoveryByQuantity({
    quantity: quantity.value,
    averageCost: averageCost.value,
    currentPrice: currentPrice.value,
    additionalQuantity: additionalQuantity.value,
    additionalPrice: additionalPrice.value,
  }),
)

const targetCalculation = computed(() =>
  calculateRecoveryByTarget({
    quantity: quantity.value,
    averageCost: averageCost.value,
    currentPrice: currentPrice.value,
    additionalPrice: additionalPrice.value,
    targetGainPercent: targetGainPercent.value,
    lotSize: lotSize.value,
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

function resetCalculator() {
  mode.value = 'QUANTITY'
  quantity.value = defaults.quantity
  averageCost.value = defaults.averageCost
  currentPrice.value = defaults.currentPrice
  lotSize.value = defaults.lotSize
  additionalPrice.value = defaults.currentPrice
  additionalQuantity.value = defaults.additionalQuantity
  targetGainPercent.value = defaults.targetGainPercent
}

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
.calculator-workspace {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.calculator-panel,
.result-panel {
  display: flex;
  height: 100%;
  min-width: 0;
  padding: 20px;
  flex-direction: column;
}

.panel-header {
  display: flex;
  min-height: 32px;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-header h2 {
  margin: 0;
  font-size: 18px;
}

.panel-header span,
.input-grid span,
.plan-grid span,
.result-grid span,
.result-footnote {
  color: var(--qg-text-muted);
  font-size: 12px;
}

.input-grid,
.plan-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.input-grid label,
.plan-grid label {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.input-grid :deep(.el-input-number),
.plan-grid :deep(.el-input-number) {
  width: 100%;
  height: 34px;
}

.position-status {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding: 11px 12px;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
  background: var(--qg-subtle);
  color: var(--qg-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.position-status strong {
  flex: 0 0 auto;
  font-size: 16px;
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
  margin: 18px 0 16px;
}

.result-grid {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-auto-rows: minmax(82px, 1fr);
  overflow: hidden;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
}

.result-grid div {
  display: grid;
  gap: 6px;
  align-content: center;
  min-width: 0;
  padding: 14px 16px;
  border-right: 1px solid var(--qg-border);
  border-bottom: 1px solid var(--qg-border);
}

.result-grid div:nth-child(2n) {
  border-right: 0;
}

.result-grid div:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.result-grid strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.calculation-error {
  padding: 14px;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
  color: var(--qg-text-muted);
  background: var(--qg-subtle);
}

.result-footnote {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

@media (max-width: 1060px) {
  .calculator-workspace {
    grid-template-columns: 1fr;
  }

  .result-grid {
    grid-auto-rows: minmax(76px, auto);
  }
}

@media (max-width: 620px) {
  .calculator-panel,
  .result-panel {
    padding: 16px;
  }

  .input-grid,
  .plan-grid {
    grid-template-columns: 1fr;
  }

  .position-status {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
