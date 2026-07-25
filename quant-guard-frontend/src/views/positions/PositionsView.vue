<template>
  <section class="page">
    <PageHeader
      v-if="!embedded"
      title="持仓收益"
      description="查看当前持仓收益、收益率、持仓市值和持仓成本。"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
    <el-alert
      v-if="!portfolioStore.selectedPortfolioId"
      title="请选择一个模拟账户后查看持仓收益。"
      type="info"
      :closable="false"
    />

    <div v-if="embedded" class="embedded-heading">
      <h2>持仓收益</h2>
    </div>

    <div class="surface performance-summary">
      <div class="performance-item primary">
        <span>当前收益金额</span>
        <strong :class="profitClass(totalProfitAmount)"
          ><MoneyText :value="totalProfitAmount"
        /></strong>
        <small>按最新日线收盘价估算</small>
      </div>
      <div class="performance-item primary">
        <span>当前收益率</span>
        <strong :class="profitClass(totalProfitRate)">{{ formatPercent(totalProfitRate) }}</strong>
        <small>收益金额 / 持仓成本</small>
      </div>
      <div class="performance-item">
        <span>持仓市值</span>
        <strong><MoneyText :value="totalMarketValue" /></strong>
        <small>当前价 × 总数量</small>
      </div>
      <div class="performance-item">
        <span>持仓成本</span>
        <strong><MoneyText :value="totalCostValue" /></strong>
        <small>平均成本 × 总数量</small>
      </div>
    </div>

    <div v-loading="loading" class="position-card-grid">
      <div
        v-for="position in positions"
        :key="position.id"
        class="surface position-card"
        :class="profitClass(profitAmount(position))"
      >
        <div class="position-card-heading">
          <div class="position-identity">
            <strong>{{ instrumentText(position.instrumentId, 'symbol') }}</strong>
            <span>{{ instrumentText(position.instrumentId, 'name') }}</span>
          </div>
          <div class="position-card-actions">
            <el-tag effect="plain">{{
              latestTradeDate(position.instrumentId) || '暂无行情'
            }}</el-tag>
            <el-button
              v-if="hasPositionQuantity(position)"
              class="recovery-button"
              size="small"
              type="primary"
              @click="openRecoveryCalculator(position)"
            >
              回本计算
            </el-button>
            <el-button size="small" @click="openEditDialog(position)">编辑</el-button>
            <el-tooltip
              :content="
                canDeletePosition(position)
                  ? '删除已清仓的空持仓记录'
                  : '仍有数量或冻结数量，需要先卖出并完成清仓'
              "
              placement="top"
            >
              <span>
                <el-button
                  size="small"
                  type="danger"
                  plain
                  :disabled="!canDeletePosition(position)"
                  :loading="operatingId === position.id"
                  @click="removePosition(position)"
                >
                  删除
                </el-button>
              </span>
            </el-tooltip>
          </div>
        </div>
        <div class="position-card-profit">
          <strong
            ><MoneyText
              v-if="profitAmount(position) !== null"
              :value="profitAmount(position)"
            /><span v-else>暂无行情</span></strong
          >
          <span>{{ formatPercent(profitRate(position)) }}</span>
        </div>
        <div class="position-card-metrics">
          <div v-for="metric in positionMetrics(position)" :key="metric.label">
            <span>{{ metric.label }}</span>
            <strong>
              <MoneyText v-if="metric.value !== null" :value="metric.value" />
              <span v-else>暂无行情</span>
            </strong>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="editDialogVisible"
      title="调整持仓数量与成本"
      width="min(460px, calc(100vw - 32px))"
    >
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="92px">
        <el-form-item label="总数量" prop="totalQuantity">
          <el-input v-model="editForm.totalQuantity" placeholder="例如：100" />
        </el-form-item>
        <el-form-item label="可用数量" prop="availableQuantity">
          <el-input v-model="editForm.availableQuantity" placeholder="例如：100" />
        </el-form-item>
        <el-form-item label="持有成本" prop="averageCost">
          <el-input v-model="editForm.averageCost" placeholder="例如：12.3456" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelEdit">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <RecoveryCalculatorDialog
      v-if="calculatorPosition && calculatorCurrentPrice !== null"
      v-model="calculatorVisible"
      :symbol="instrumentText(calculatorPosition.instrumentId, 'symbol')"
      :quantity="Number(calculatorPosition.totalQuantity)"
      :average-cost="Number(calculatorPosition.averageCost)"
      :current-price="calculatorCurrentPrice"
      :lot-size="instrumentLotSize(calculatorPosition.instrumentId)"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import { listInstruments } from '@/api/instrument'
import { getInstrumentDailyPriceSummary } from '@/api/marketData'
import { deletePosition, listPositionsByPortfolio, updatePosition } from '@/api/position'
import { usePortfolioStore } from '@/stores/portfolio'
import type { SecurityInstrument } from '@/types/instrument'
import type { DailyPriceSummary } from '@/types/marketData'
import type { PortfolioPosition } from '@/types/position'
import RecoveryCalculatorDialog from './components/RecoveryCalculatorDialog.vue'

const portfolioStore = usePortfolioStore()

defineProps<{
  embedded?: boolean
}>()

const positions = ref<PortfolioPosition[]>([])
const instruments = ref<SecurityInstrument[]>([])
const priceSummaries = ref<Record<number, DailyPriceSummary | null>>({})
const loading = ref(false)
const submitting = ref(false)
const operatingId = ref<number | null>(null)
const errorMessage = ref('')
const editingPosition = ref<PortfolioPosition | null>(null)
const calculatorPosition = ref<PortfolioPosition | null>(null)
const calculatorVisible = ref(false)
const editDialogVisible = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = ref({
  totalQuantity: '',
  availableQuantity: '',
  averageCost: '',
})

const totalCostValue = computed<number>(() =>
  sumNumbers(positions.value.map((position) => positionValue(position, 'cost'))),
)
const totalMarketValue = computed<number>(() =>
  sumNumbers(positions.value.map((position) => positionValue(position, 'market'))),
)
const totalProfitAmount = computed<number>(() => totalMarketValue.value - totalCostValue.value)
const totalProfitRate = computed(() =>
  totalCostValue.value > 0 ? totalProfitAmount.value / totalCostValue.value : null,
)

const calculatorCurrentPrice = computed(() => {
  const position = calculatorPosition.value
  if (!position) return null
  const marketPrice = latestClose(position.instrumentId)
  if (marketPrice !== null && marketPrice > 0) return marketPrice
  const averageCost = Number(position.averageCost)
  return Number.isFinite(averageCost) && averageCost > 0 ? averageCost : 1
})

const decimalPattern = /^(0|[1-9]\d{0,14})(\.\d{1,4})?$/
const editRules: FormRules = {
  totalQuantity: [
    { required: true, message: '请输入总数量', trigger: 'blur' },
    { pattern: decimalPattern, message: '请输入非负数字，最多 4 位小数', trigger: 'blur' },
  ],
  availableQuantity: [
    { required: true, message: '请输入可用数量', trigger: 'blur' },
    { pattern: decimalPattern, message: '请输入非负数字，最多 4 位小数', trigger: 'blur' },
  ],
  averageCost: [
    { required: true, message: '请输入持有成本', trigger: 'blur' },
    { pattern: decimalPattern, message: '请输入非负数字，最多 4 位小数', trigger: 'blur' },
  ],
}

onMounted(() => {
  void loadPositions()
})

watch(
  () => portfolioStore.selectedPortfolioId,
  () => {
    void loadPositions()
  },
)

async function loadPositions() {
  if (!portfolioStore.selectedPortfolioId) {
    positions.value = []
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const [positionRows, instrumentRows] = await Promise.all([
      listPositionsByPortfolio(portfolioStore.selectedPortfolioId),
      listInstruments(),
    ])
    positions.value = positionRows
    instruments.value = instrumentRows
    await loadPriceSummaries(positionRows)
  } catch {
    errorMessage.value = '持仓加载失败，请确认后端服务是否已启动。'
  } finally {
    loading.value = false
  }
}

async function loadPriceSummaries(positionRows: PortfolioPosition[]) {
  const instrumentIds = Array.from(new Set(positionRows.map((position) => position.instrumentId)))
  const entries = await Promise.all(
    instrumentIds.map(async (instrumentId) => {
      try {
        const summary = await getInstrumentDailyPriceSummary(instrumentId)
        return [instrumentId, summary] as const
      } catch {
        return [instrumentId, null] as const
      }
    }),
  )
  priceSummaries.value = Object.fromEntries(entries)
}

async function removePosition(position: PortfolioPosition) {
  if (!canDeletePosition(position)) {
    ElMessage.warning('仍有数量或冻结数量，需通过卖出和成交清仓后才能删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `删除“${instrumentText(position.instrumentId, 'symbol')}”的空持仓记录？\n\n该操作只清理数量为 0 的持仓记录，不会删除订单、成交或资金流水。`,
      '删除空持仓',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  operatingId.value = position.id
  try {
    await deletePosition(position.id)
    ElMessage.success('空持仓已删除')
    await loadPositions()
  } finally {
    operatingId.value = null
  }
}

function openEditDialog(position: PortfolioPosition) {
  editingPosition.value = position
  editForm.value = {
    totalQuantity: String(position.totalQuantity),
    availableQuantity: String(position.availableQuantity),
    averageCost: String(position.averageCost),
  }
  editDialogVisible.value = true
}

function openRecoveryCalculator(position: PortfolioPosition) {
  calculatorPosition.value = position
  calculatorVisible.value = true
}

function cancelEdit() {
  editDialogVisible.value = false
  editingPosition.value = null
  editFormRef.value?.clearValidate()
}

async function submitEdit() {
  if (!editingPosition.value || !editFormRef.value) return
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (Number(editForm.value.availableQuantity) > Number(editForm.value.totalQuantity)) {
    ElMessage.warning('可用数量不能大于总数量')
    return
  }

  submitting.value = true
  try {
    await updatePosition(editingPosition.value.id, {
      totalQuantity: editForm.value.totalQuantity.trim(),
      availableQuantity: editForm.value.availableQuantity.trim(),
      averageCost: editForm.value.averageCost.trim(),
    })
    ElMessage.success('持仓已更新')
    cancelEdit()
    await loadPositions()
  } finally {
    submitting.value = false
  }
}

function canDeletePosition(position: PortfolioPosition) {
  return Number(position.totalQuantity) === 0 && Number(position.frozenQuantity) === 0
}

function instrumentText(instrumentId: number, field: 'symbol' | 'name') {
  const instrument = instruments.value.find((item) => item.id === instrumentId)
  if (!instrument) return field === 'symbol' ? `标的 ID: ${instrumentId}` : '-'
  return field === 'symbol' ? instrument.symbol : instrument.instrumentName
}

function instrumentLotSize(instrumentId: number) {
  const lotSize = Number(instruments.value.find((item) => item.id === instrumentId)?.lotSize ?? 1)
  return Number.isFinite(lotSize) && lotSize > 0 ? lotSize : 1
}

function latestClose(instrumentId: number) {
  const value = priceSummaries.value[instrumentId]?.latestClose
  if (value === null || value === undefined) return null
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

function latestTradeDate(instrumentId: number) {
  return priceSummaries.value[instrumentId]?.latestTradeDate ?? ''
}

function positionMetrics(position: PortfolioPosition) {
  return [
    { label: '当前价', value: latestClose(position.instrumentId) },
    { label: '平均成本', value: position.averageCost },
    { label: '总数量', value: position.totalQuantity },
    { label: '可用数量', value: position.availableQuantity },
  ]
}

function profitAmount(position: PortfolioPosition) {
  const close = latestClose(position.instrumentId)
  if (close === null) return null
  const quantity = Number(position.totalQuantity)
  const averageCost = Number(position.averageCost)
  if (!Number.isFinite(quantity) || !Number.isFinite(averageCost)) return null
  return (close - averageCost) * quantity
}

function positionValue(position: PortfolioPosition, kind: 'cost' | 'market') {
  const quantity = Number(position.totalQuantity)
  if (!Number.isFinite(quantity)) return null
  const price = kind === 'cost' ? Number(position.averageCost) : latestClose(position.instrumentId)
  if (price === null || !Number.isFinite(price)) return null
  return price * quantity
}

function sumNumbers(values: Array<number | null>): number {
  return values.reduce<number>((total, value) => total + (value ?? 0), 0)
}

function profitRate(position: PortfolioPosition) {
  const close = latestClose(position.instrumentId)
  const averageCost = Number(position.averageCost)
  if (close === null || !Number.isFinite(averageCost) || averageCost <= 0) return null
  return (close - averageCost) / averageCost
}

function hasPositionQuantity(position: PortfolioPosition) {
  const quantity = Number(position.totalQuantity)
  return Number.isFinite(quantity) && quantity > 0
}

function formatPercent(value: number | null) {
  if (value === null || !Number.isFinite(value)) return '暂无行情'
  return new Intl.NumberFormat('zh-CN', {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function profitClass(value: number | null) {
  if (value === null || value === 0) return 'muted'
  return value > 0 ? 'profit-positive' : 'profit-negative'
}
</script>

<style scoped>
.embedded-heading {
  min-width: 0;
  margin-bottom: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--qg-border);
}

.embedded-heading h2 {
  margin: 0;
  font-size: 20px;
}

.performance-summary {
  display: grid;
  grid-template-columns: 1.15fr 1fr 1fr 1fr;
  overflow: hidden;
  margin-bottom: 12px;
}

.performance-item {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 16px;
  border-right: 1px solid var(--qg-border);
}

.performance-item:last-child {
  border-right: 0;
}

.performance-item > span,
.performance-item > small,
.position-identity > span,
.position-card-metrics div > span {
  color: var(--qg-text-muted);
  font-size: 13px;
}

.performance-item strong {
  font-size: 24px;
  line-height: 1.1;
}

.performance-item.primary strong {
  font-size: 28px;
}

.position-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.position-card {
  display: grid;
  gap: 16px;
  min-width: 0;
  padding: 16px;
  border-left: 4px solid var(--qg-border);
}

.position-card.profit-positive {
  border-left-color: #d93025;
}

.position-card.profit-negative {
  border-left-color: #188038;
}

.position-card-heading {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
}

.position-identity {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.position-identity strong,
.position-identity span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.position-card-actions {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.recovery-button {
  --el-button-bg-color: #171717;
  --el-button-border-color: #171717;
  --el-button-text-color: #ffffff;
  --el-button-hover-bg-color: #404040;
  --el-button-hover-border-color: #404040;
  --el-button-active-bg-color: #000000;
  --el-button-active-border-color: #000000;
}

:global(html.dark) .recovery-button {
  --el-button-bg-color: #f4f4f5;
  --el-button-border-color: #f4f4f5;
  --el-button-text-color: #18181b;
  --el-button-hover-bg-color: #e4e4e7;
  --el-button-hover-border-color: #e4e4e7;
  --el-button-hover-text-color: #18181b;
  --el-button-active-bg-color: #d4d4d8;
  --el-button-active-border-color: #d4d4d8;
}

.position-card-profit {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.position-card-profit strong {
  font-size: 28px;
}

.position-card-profit > span {
  font-size: 18px;
  font-weight: 700;
}

.position-card-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.position-card-metrics div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.muted {
  color: var(--qg-text-muted);
}

.profit-positive {
  color: #d93025;
  font-weight: 600;
}

.profit-negative {
  color: #188038;
  font-weight: 600;
}

@media (max-width: 1200px) {
  .performance-summary,
  .position-card-grid,
  .position-card-metrics {
    grid-template-columns: 1fr 1fr;
  }

  .performance-item:nth-child(2) {
    border-right: 0;
  }

  .performance-item:nth-child(-n + 2) {
    border-bottom: 1px solid var(--qg-border);
  }
}

@media (max-width: 760px) {
  .performance-summary,
  .position-card-grid,
  .position-card-metrics,
  .position-card-profit {
    grid-template-columns: 1fr;
  }

  .performance-item {
    border-right: 0;
    border-bottom: 1px solid var(--qg-border);
  }

  .performance-item:last-child {
    border-bottom: 0;
  }

  .position-card-profit {
    display: grid;
  }

  .position-card-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .position-card-actions {
    justify-content: flex-start;
  }
}
</style>
