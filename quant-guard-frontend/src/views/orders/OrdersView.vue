<template>
  <section class="page">
    <PageHeader title="模拟订单" />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />

    <div v-if="!selectedPortfolioId" class="surface selection-empty">
      <div>
        <h2>先选择一个模拟账户</h2>
        <el-button type="primary" @click="router.push('/accounts')">选择账户</el-button>
      </div>
    </div>

    <el-alert
      v-if="selectionMismatch"
      title="当前交易环境不可用"
      description="已禁止提交订单，请回到模拟账户页面重新选择。"
      type="warning"
      :closable="false"
    >
      <template #default>
        <el-button type="primary" @click="router.push('/accounts')">重新选择</el-button>
      </template>
    </el-alert>

    <div v-if="selectedPortfolioId" class="work-grid">
      <div class="surface order-form-panel">
        <div class="panel-heading">
          <h2>创建订单</h2>
        </div>

        <el-alert
          v-if="activeInstruments.length === 0 && !loading"
          title="当前没有可下单的证券"
          description="请先在行情中心导入并启用证券。"
          type="info"
          :closable="false"
        />

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          hide-required-asterisk
          class="order-form"
        >
          <el-form-item label="证券标的" prop="instrumentId">
            <el-select
              v-model="form.instrumentId"
              filterable
              class="full-width"
              placeholder="请选择 ACTIVE 证券"
              :disabled="activeInstruments.length === 0"
            >
              <el-option
                v-for="instrument in activeInstruments"
                :key="instrument.id"
                :label="instrumentOptionLabel(instrument)"
                :value="instrument.id"
              />
            </el-select>
          </el-form-item>

          <div v-if="selectedInstrument" class="position-snapshot">
            <div class="snapshot-grid">
              <div>
                <span>持仓状态</span>
                <strong>{{ selectedPosition ? '真实持仓' : '暂无持仓' }}</strong>
              </div>
              <div>
                <span>资产类型</span>
                <strong>{{ assetTypeLabel(selectedInstrument.assetType) }}</strong>
              </div>
              <div>
                <span>币种</span>
                <strong>{{ selectedInstrument.currency }}</strong>
              </div>
              <div>
                <span>最小交易单位</span>
                <strong>{{ selectedInstrument.lotSize }}</strong>
              </div>
              <div>
                <span>总数量</span>
                <strong><MoneyText :value="selectedPosition?.totalQuantity ?? 0" /></strong>
              </div>
              <div>
                <span>可用数量</span>
                <strong><MoneyText :value="selectedPosition?.availableQuantity ?? 0" /></strong>
              </div>
              <div>
                <span>冻结数量</span>
                <strong><MoneyText :value="selectedPosition?.frozenQuantity ?? 0" /></strong>
              </div>
              <div>
                <span>平均成本</span>
                <strong><MoneyText :value="selectedPosition?.averageCost ?? 0" /></strong>
              </div>
            </div>
          </div>

          <el-form-item label="买卖" prop="side">
            <el-radio-group v-model="form.side" class="side-group">
              <el-radio-button value="BUY">买入</el-radio-button>
              <el-radio-button value="SELL">卖出</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <div class="order-input-grid">
            <el-form-item label="数量" prop="requestedQuantity">
              <el-input v-model.trim="form.requestedQuantity" inputmode="decimal" />
            </el-form-item>

            <el-form-item label="单价" prop="limitPrice">
              <el-input v-model.trim="form.limitPrice" inputmode="decimal" />
            </el-form-item>
          </div>

          <div class="estimate-box">
            <span>预估金额</span>
            <strong>{{ estimatedAmountText }}</strong>
          </div>

          <el-alert
            v-if="submitBlockReason"
            :title="submitBlockReason"
            type="warning"
            :closable="false"
          />

          <div class="form-actions">
            <el-button :disabled="submitting" @click="resetOrderForm">清空</el-button>
            <el-button
              type="primary"
              :loading="submitting"
              :disabled="Boolean(submitBlockReason)"
              @click="submitOrder"
            >
              提交订单
            </el-button>
          </div>
        </el-form>
      </div>

      <div class="side-stack">
        <div class="surface market-panel">
          <div class="panel-heading chart-toolbar">
            <el-radio-group v-model="dailyPriceRange" size="small" @change="loadDailyPrices">
              <el-radio-button value="1M">1月</el-radio-button>
              <el-radio-button value="3M">3月</el-radio-button>
              <el-radio-button value="6M">6月</el-radio-button>
              <el-radio-button value="1Y">1年</el-radio-button>
              <el-radio-button value="3Y">3年</el-radio-button>
              <el-radio-button value="ALL">全部</el-radio-button>
            </el-radio-group>
          </div>

          <div v-if="selectedDailyPrice" class="selected-candle">
            <div>
              <span>{{ selectedDailyPrice.tradeDate }} 收盘价</span>
              <strong><MoneyText :value="selectedDailyPrice.close" /></strong>
            </div>
            <el-space>
              <el-button
                size="small"
                type="success"
                @click="applyDailyPrice(selectedDailyPrice, 'BUY')"
              >
                用此价买入
              </el-button>
              <el-button
                size="small"
                type="warning"
                @click="applyDailyPrice(selectedDailyPrice, 'SELL')"
              >
                用此价卖出
              </el-button>
            </el-space>
          </div>

          <el-alert
            v-if="dailyPriceError"
            :title="dailyPriceError"
            type="warning"
            :closable="false"
          />
          <el-empty v-if="!selectedInstrument" description="请选择证券标的后查看 K 线" />
          <el-empty
            v-else-if="!dailyPriceLoading && dailyPrices.length === 0"
            description="当前证券暂无历史日线，请先到证券与行情管理中同步。"
          />
          <CandlestickChart
            v-else
            v-loading="dailyPriceLoading"
            :prices="dailyPrices"
            @candle-click="handleDailyPriceClick"
          />
        </div>
      </div>
    </div>

    <div v-if="selectedPortfolioId" class="surface section-body">
      <div class="table-heading">
        <h2>订单与成交</h2>
        <span>{{ orders.length }} 条订单 · {{ trades.length }} 条成交</span>
      </div>

      <el-table
        v-loading="loading"
        class="order-table"
        :data="orders"
        row-key="id"
        empty-text="暂无模拟订单"
        max-height="calc(100vh - 280px)"
        table-layout="fixed"
      >
        <el-table-column label="标的" min-width="160">
          <template #default="{ row }: { row: SimulatedOrder }">
            <div class="order-security">
              <strong>{{ instrumentText(row.instrumentId, 'symbol') }}</strong>
              <span>{{ instrumentText(row.instrumentId, 'name') }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="80">
          <template #default="{ row }: { row: SimulatedOrder }">
            <el-tag :type="row.side === 'BUY' ? 'success' : 'warning'" effect="plain">
              {{ sideLabel(row.side) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数量" min-width="96" align="right">
          <template #default="{ row }: { row: SimulatedOrder }">
            <MoneyText :value="row.requestedQuantity" />
          </template>
        </el-table-column>
        <el-table-column label="单价" min-width="96" align="right">
          <template #default="{ row }: { row: SimulatedOrder }">
            <MoneyText :value="row.limitPrice" />
          </template>
        </el-table-column>
        <el-table-column label="委托金额" min-width="118" align="right">
          <template #default="{ row }: { row: SimulatedOrder }">
            <MoneyText :value="orderAmount(row)" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }: { row: SimulatedOrder }">
            <el-tooltip
              :content="row.rejectionReason || orderStatusLabel(row.status)"
              placement="top"
            >
              <el-tag :type="orderStatusTag(row.status)" effect="plain">
                {{ orderStatusLabel(row.status) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="成交结果" min-width="180">
          <template #default="{ row }: { row: SimulatedOrder }">
            <el-tooltip
              v-if="tradeForOrder(row.id)"
              :content="`成交编号：${tradeForOrder(row.id)?.tradeNo}`"
              placement="top"
            >
              <div class="trade-summary">
                <strong>
                  成交价
                  <MoneyText :value="tradeForOrder(row.id)?.executionPrice" />
                </strong>
                <span>
                  <MoneyText :value="tradeForOrder(row.id)?.tradeAmount" />
                  · {{ formatDateTime(tradeForOrder(row.id)?.executedAt ?? '') }}
                </span>
              </div>
            </el-tooltip>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="155">
          <template #default="{ row }: { row: SimulatedOrder }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }: { row: SimulatedOrder }">
            <el-space v-if="row.status === 'OPEN'">
              <el-button
                link
                type="primary"
                :loading="isActionLoading(row.id, 'execute')"
                :disabled="isOrderActionDisabled(row.id)"
                @click="confirmExecute(row)"
              >
                执行
              </el-button>
              <el-button
                link
                type="danger"
                :loading="isActionLoading(row.id, 'cancel')"
                :disabled="isOrderActionDisabled(row.id)"
                @click="confirmCancel(row)"
              >
                取消
              </el-button>
            </el-space>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { AxiosError } from 'axios'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules, TagProps } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import { listAccounts } from '@/api/account'
import { listInstruments } from '@/api/instrument'
import { listInstrumentDailyPrices } from '@/api/marketData'
import { cancelOrder, createOrder, executeOrder, listOrdersByPortfolioId } from '@/api/order'
import { listPortfolios } from '@/api/portfolio'
import { listPositionsByPortfolio } from '@/api/position'
import { listTradesByPortfolioId } from '@/api/trade'
import CandlestickChart from '@/views/market-data/components/CandlestickChart.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import type { SimulationAccount } from '@/types/account'
import type { SecurityInstrument, AssetType } from '@/types/instrument'
import type { DailyPrice } from '@/types/marketData'
import type {
  CreateSimulatedOrderRequest,
  OrderSide,
  OrderStatus,
  SimulatedOrder,
} from '@/types/order'
import type { InvestmentPortfolio } from '@/types/portfolio'
import type { PortfolioPosition } from '@/types/position'
import type { SimulatedTrade } from '@/types/trade'

interface OrderForm {
  instrumentId: number | null
  side: OrderSide
  requestedQuantity: string
  limitPrice: string
}

type InstrumentTextField = 'symbol' | 'name'
type OrderAction = 'execute' | 'cancel'
type DailyPriceRange = '1M' | '3M' | '6M' | '1Y' | '3Y' | 'ALL'

const router = useRouter()
const route = useRoute()
const portfolioStore = usePortfolioStore()

const accounts = ref<SimulationAccount[]>([])
const portfolios = ref<InvestmentPortfolio[]>([])
const instruments = ref<SecurityInstrument[]>([])
const orders = ref<SimulatedOrder[]>([])
const trades = ref<SimulatedTrade[]>([])
const positions = ref<PortfolioPosition[]>([])
const dailyPrices = ref<DailyPrice[]>([])
const selectedDailyPrice = ref<DailyPrice | null>(null)
const loading = ref(false)
const dailyPriceLoading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const dailyPriceError = ref('')
const formRef = ref<FormInstance>()
const activeActionOrderId = ref<number | null>(null)
const activeAction = ref<OrderAction | null>(null)
const dailyPriceRange = ref<DailyPriceRange>('6M')
let requestSeq = 0

const form = reactive<OrderForm>({
  instrumentId: null,
  side: 'BUY',
  requestedQuantity: '',
  limitPrice: '',
})

const selectedAccountId = computed(() => portfolioStore.selectedAccountId)
const selectedPortfolioId = computed(() => portfolioStore.selectedPortfolioId)
const currentPortfolio = computed(() =>
  portfolios.value.find((portfolio) => portfolio.id === selectedPortfolioId.value),
)
const currentAccount = computed(() =>
  accounts.value.find((account) => account.id === selectedAccountId.value),
)
const activeInstruments = computed(() =>
  instruments.value.filter(
    (instrument) => instrument.status === 'ACTIVE' && instrument.assetType !== 'INDEX',
  ),
)
const selectedInstrument = computed(() =>
  activeInstruments.value.find((instrument) => instrument.id === form.instrumentId),
)
const selectedPosition = computed(() =>
  positions.value.find((position) => position.instrumentId === form.instrumentId),
)
const tradesByOrderId = computed(() => new Map(trades.value.map((trade) => [trade.orderId, trade])))
const selectionMismatch = computed(() => {
  if (!selectedAccountId.value || !currentPortfolio.value) return false
  return currentPortfolio.value.accountId !== selectedAccountId.value
})
const estimatedAmount = computed(() => multiplyDecimal(form.requestedQuantity, form.limitPrice, 4))
const estimatedAmountText = computed(() => {
  if (!estimatedAmount.value) return '-'
  const currency = selectedInstrument.value?.currency
  return currency ? `${estimatedAmount.value} ${currency}` : estimatedAmount.value
})
const submitBlockReason = computed(() => {
  if (!selectedPortfolioId.value) return '请先选择模拟账户'
  if (!currentPortfolio.value) return '当前账户的交易环境不存在或已不可用'
  if (!selectedAccountId.value || !currentAccount.value) return '当前账户不存在或已不可用'
  if (currentAccount.value.status !== 'ACTIVE') return '当前账户已停用，请重新选择启用账户'
  if (currentPortfolio.value.status !== 'ACTIVE') return '当前账户的交易环境已停用'
  if (selectionMismatch.value) return '当前交易环境与账户不匹配'
  if (activeInstruments.value.length === 0) return '当前没有可用证券标的'
  return ''
})

const rules: FormRules<OrderForm> = {
  instrumentId: [{ required: true, message: '请选择证券标的', trigger: 'change' }],
  side: [{ required: true, message: '请选择买卖方向', trigger: 'change' }],
  requestedQuantity: [{ validator: validateQuantity, trigger: 'blur' }],
  limitPrice: [{ validator: validateLimitPrice, trigger: 'blur' }],
}

onMounted(() => {
  void loadPageData()
})

watch(
  () => portfolioStore.selectedPortfolioId,
  () => {
    void loadPageData()
  },
)

watch(
  () => form.instrumentId,
  () => {
    formRef.value?.clearValidate('requestedQuantity')
    selectedDailyPrice.value = null
    void loadDailyPrices()
  },
)

watch(
  () => route.query.instrumentId,
  () => {
    const preferredInstrumentId = queryInstrumentId()
    if (preferredInstrumentId) form.instrumentId = preferredInstrumentId
  },
)

async function loadPageData() {
  const portfolioId = selectedPortfolioId.value
  const seq = ++requestSeq
  loading.value = true
  errorMessage.value = ''
  orders.value = []
  trades.value = []
  positions.value = []

  try {
    const [accountRows, portfolioRows, instrumentRows] = await Promise.all([
      listAccounts(),
      listPortfolios(),
      listInstruments(),
    ])
    if (seq !== requestSeq) return
    accounts.value = accountRows
    portfolios.value = portfolioRows
    instruments.value = instrumentRows
    reconcileSelection()

    const preferredInstrumentId = queryInstrumentId()
    const activeSelected = activeInstruments.value.some(
      (instrument) => instrument.id === form.instrumentId,
    )
    if (preferredInstrumentId) {
      form.instrumentId = preferredInstrumentId
    } else if (!activeSelected) {
      form.instrumentId = activeInstruments.value[0]?.id ?? null
    }

    const reconciledPortfolioId = selectedPortfolioId.value
    if (!portfolioId || !reconciledPortfolioId) return
    if (reconciledPortfolioId !== portfolioId) return
    if (selectionMismatch.value) return

    await Promise.all([
      loadOrders(reconciledPortfolioId, seq),
      loadTrades(reconciledPortfolioId, seq),
      loadPositions(reconciledPortfolioId, seq),
      loadDailyPrices(),
    ])
  } catch (error) {
    if (seq !== requestSeq) return
    errorMessage.value = errorToMessage(error, '模拟订单页面加载失败，请确认后端服务是否已启动。')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function queryInstrumentId() {
  const raw = route.query.instrumentId
  const value = Array.isArray(raw) ? raw[0] : raw
  const id = value ? Number(value) : NaN
  if (!Number.isInteger(id) || id <= 0) return null
  return activeInstruments.value.some((instrument) => instrument.id === id) ? id : null
}

async function loadPositions(portfolioId: number, seq: number) {
  try {
    const rows = await listPositionsByPortfolio(portfolioId)
    if (seq !== requestSeq) return
    positions.value = rows
  } catch (error) {
    if (seq !== requestSeq) return
    positions.value = []
    errorMessage.value = errorToMessage(error, '持仓加载失败，请稍后重试。')
  }
}

async function loadOrders(portfolioId: number, seq: number) {
  try {
    const rows = await listOrdersByPortfolioId(portfolioId)
    if (seq !== requestSeq) return
    orders.value = rows
  } catch (error) {
    if (seq !== requestSeq) return
    orders.value = []
    errorMessage.value = errorToMessage(error, '订单列表加载失败，请稍后重试。')
  }
}

async function loadTrades(portfolioId: number, seq: number) {
  try {
    const rows = await listTradesByPortfolioId(portfolioId)
    if (seq !== requestSeq) return
    trades.value = rows
  } catch (error) {
    if (seq !== requestSeq) return
    trades.value = []
    errorMessage.value = errorToMessage(error, '成交记录加载失败，请稍后重试。')
  }
}

async function loadDailyPrices() {
  const instrumentId = form.instrumentId
  selectedDailyPrice.value = null
  dailyPriceError.value = ''

  if (!instrumentId) {
    dailyPrices.value = []
    return
  }

  dailyPriceLoading.value = true
  try {
    dailyPrices.value = await listInstrumentDailyPrices(instrumentId, dailyPriceQuery())
  } catch (error) {
    dailyPrices.value = []
    dailyPriceError.value = errorToMessage(
      error,
      'K 线加载失败，请确认后端服务和行情数据是否可用。',
    )
  } finally {
    dailyPriceLoading.value = false
  }
}

function handleDailyPriceClick(price: DailyPrice) {
  applyDailyPrice(price, form.side)
}

function applyDailyPrice(price: DailyPrice, side: OrderSide) {
  selectedDailyPrice.value = price
  form.side = side
  form.limitPrice = formatLimitPrice(price.close)
  formRef.value?.clearValidate('limitPrice')
  ElMessage.success(`已带入 ${price.tradeDate} 收盘价`)
}

async function submitOrder() {
  if (!formRef.value || submitBlockReason.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!selectedAccountId.value || !selectedPortfolioId.value || !form.instrumentId) return

  submitting.value = true
  errorMessage.value = ''

  const payload: CreateSimulatedOrderRequest = {
    accountId: selectedAccountId.value,
    portfolioId: selectedPortfolioId.value,
    instrumentId: form.instrumentId,
    side: form.side,
    orderType: 'LIMIT',
    requestedQuantity: form.requestedQuantity,
    limitPrice: Number(form.limitPrice).toFixed(4),
  }

  try {
    const created = await createOrder(payload)
    ElMessage.success(`订单已创建：${created.orderNo}，服务端已冻结相应资金或持仓`)
    form.requestedQuantity = ''
    form.limitPrice = ''
    formRef.value.clearValidate()
    const seq = ++requestSeq
    loading.value = true
    await Promise.all([
      loadOrders(selectedPortfolioId.value, seq),
      loadAccountsAndPositionsAfterAction(selectedPortfolioId.value, seq),
    ])
  } catch (error) {
    errorMessage.value = errorToMessage(error, '订单创建失败，请检查输入后重试。')
  } finally {
    submitting.value = false
    loading.value = false
  }
}

async function confirmExecute(order: SimulatedOrder) {
  const sideDescription =
    order.side === 'BUY' ? '将消耗已冻结资金并增加持仓。' : '将扣减已冻结持仓并增加账户可用资金。'
  await ElMessageBox.confirm(
    `确认执行订单 ${order.orderNo}？系统将按该单价立即完成模拟成交，并更新资金、持仓、成交和资金流水。该操作完成后不能取消。${sideDescription}`,
    '执行模拟订单',
    {
      confirmButtonText: '执行',
      cancelButtonText: '取消',
      type: 'warning',
    },
  )
    .then(() => operateOrder(order, 'execute'))
    .catch(() => undefined)
}

async function confirmCancel(order: SimulatedOrder) {
  const sideDescription =
    order.side === 'BUY' ? '取消后将释放订单冻结资金。' : '取消后将释放订单冻结持仓。'
  await ElMessageBox.confirm(
    `确认取消订单 ${order.orderNo}？${sideDescription}订单记录会保留并由服务端更新状态。`,
    '取消模拟订单',
    {
      confirmButtonText: '取消订单',
      cancelButtonText: '返回',
      type: 'warning',
    },
  )
    .then(() => operateOrder(order, 'cancel'))
    .catch(() => undefined)
}

async function operateOrder(order: SimulatedOrder, action: OrderAction) {
  if (activeActionOrderId.value !== null || order.status !== 'OPEN') return
  const portfolioId = selectedPortfolioId.value
  if (!portfolioId || portfolioId !== order.portfolioId) return

  activeActionOrderId.value = order.id
  activeAction.value = action
  errorMessage.value = ''

  try {
    if (action === 'execute') {
      const result = await executeOrder(order.id)
      ElMessage.success(`订单已执行：${result.trade.tradeNo}`)
    } else {
      const canceled = await cancelOrder(order.id)
      ElMessage.success(
        `订单已取消：${orderStatusLabel(canceled.status)}，冻结资金或持仓已由服务端释放`,
      )
    }

    try {
      await refreshAfterOrderAction(portfolioId)
    } catch (refreshError) {
      errorMessage.value = errorToMessage(refreshError, '操作成功，但最新数据刷新失败。')
    }
  } catch (error) {
    errorMessage.value = errorToMessage(error, '订单操作失败，请稍后重试。')
    const seq = ++requestSeq
    await loadOrders(portfolioId, seq)
  } finally {
    activeActionOrderId.value = null
    activeAction.value = null
  }
}

async function refreshAfterOrderAction(portfolioId: number) {
  const seq = ++requestSeq
  await Promise.all([
    loadOrders(portfolioId, seq),
    loadTrades(portfolioId, seq),
    loadAccountsAndPositionsAfterAction(portfolioId, seq),
  ])
}

async function loadAccountsAndPositionsAfterAction(portfolioId: number, seq: number) {
  const [accountRows, positionRows] = await Promise.all([
    listAccounts(),
    listPositionsByPortfolio(portfolioId),
  ])
  if (seq !== requestSeq) return
  accounts.value = accountRows
  positions.value = positionRows
}

function resetOrderForm() {
  form.requestedQuantity = ''
  form.limitPrice = ''
  formRef.value?.clearValidate()
}

function dailyPriceQuery() {
  const to = new Date()
  const from = new Date(to)
  const limitByRange: Record<DailyPriceRange, number> = {
    '1M': 80,
    '3M': 160,
    '6M': 260,
    '1Y': 520,
    '3Y': 1000,
    ALL: 10000,
  }

  if (dailyPriceRange.value === '1M') from.setMonth(from.getMonth() - 1)
  if (dailyPriceRange.value === '3M') from.setMonth(from.getMonth() - 3)
  if (dailyPriceRange.value === '6M') from.setMonth(from.getMonth() - 6)
  if (dailyPriceRange.value === '1Y') from.setFullYear(from.getFullYear() - 1)
  if (dailyPriceRange.value === '3Y') from.setFullYear(from.getFullYear() - 3)
  if (dailyPriceRange.value === 'ALL') from.setFullYear(from.getFullYear() - 30)

  return {
    from: toDateString(from),
    to: toDateString(to),
    limit: limitByRange[dailyPriceRange.value],
  }
}

function toDateString(value: Date) {
  return value.toISOString().slice(0, 10)
}

function reconcileSelection() {
  const selectedAccountExists =
    portfolioStore.selectedAccountId === null ||
    accounts.value.some(
      (account) => account.id === portfolioStore.selectedAccountId && account.status === 'ACTIVE',
    )

  if (!selectedAccountExists) {
    portfolioStore.selectAccount(null)
  }

  const selectedPortfolio = portfolios.value.find(
    (portfolio) => portfolio.id === portfolioStore.selectedPortfolioId,
  )

  if (!selectedPortfolio || selectedPortfolio.status !== 'ACTIVE') {
    const availablePortfolios = portfolios.value.filter((portfolio) => {
      if (portfolio.status !== 'ACTIVE') return false
      return accounts.value.some(
        (account) => account.id === portfolio.accountId && account.status === 'ACTIVE',
      )
    })
    if (availablePortfolios.length === 1) {
      portfolioStore.selectAccount(availablePortfolios[0].accountId)
      portfolioStore.selectPortfolio(availablePortfolios[0].id)
    } else {
      portfolioStore.selectPortfolio(null)
    }
    return
  }

  if (portfolioStore.selectedAccountId !== selectedPortfolio.accountId) {
    portfolioStore.selectAccount(selectedPortfolio.accountId)
  }
}

function errorToMessage(error: unknown, fallback: string) {
  if (error instanceof AxiosError) {
    return error.response?.data?.message || fallback
  }
  if (error instanceof Error) return error.message
  return fallback
}

function validateQuantity(_: unknown, value: string, callback: (error?: Error) => void) {
  if (!/^\d{1,13}(\.\d{1,6})?$/.test(value)) {
    callback(new Error('数量必须大于 0，最多 13 位整数和 6 位小数'))
    return
  }
  if (Number(value) <= 0) {
    callback(new Error('数量必须大于 0'))
    return
  }
  if (selectedInstrument.value && !isMultipleOfLotSize(value, selectedInstrument.value.lotSize)) {
    callback(new Error(`数量必须是 lotSize ${selectedInstrument.value.lotSize} 的整数倍`))
    return
  }
  callback()
}

function validateLimitPrice(_: unknown, value: string, callback: (error?: Error) => void) {
  if (!/^\d{1,15}(\.\d{1,4})?$/.test(value)) {
    callback(new Error('单价必须大于 0，最多 15 位整数和 4 位小数'))
    return
  }
  if (Number(value) <= 0) {
    callback(new Error('单价必须大于 0'))
    return
  }
  callback()
}

function isMultipleOfLotSize(quantity: string, lotSize: string | number) {
  const parsedQuantity = parseDecimal(quantity)
  const parsedLotSize = parseDecimal(String(lotSize))
  if (!parsedQuantity || !parsedLotSize || parsedLotSize.units === 0n) return false

  const scale = Math.max(parsedQuantity.scale, parsedLotSize.scale)
  const quantityUnits = parsedQuantity.units * 10n ** BigInt(scale - parsedQuantity.scale)
  const lotUnits = parsedLotSize.units * 10n ** BigInt(scale - parsedLotSize.scale)
  return quantityUnits % lotUnits === 0n
}

function parseDecimal(value: string) {
  if (!/^\d+(\.\d+)?$/.test(value)) return null
  const [integerPart, decimalPart = ''] = value.split('.')
  return {
    units: BigInt(`${integerPart}${decimalPart}`),
    scale: decimalPart.length,
  }
}

function multiplyDecimal(left: string | number, right: string | number, scale: number) {
  const leftText = String(left)
  const rightText = String(right)
  if (!/^\d+(\.\d+)?$/.test(leftText) || !/^\d+(\.\d+)?$/.test(rightText)) return ''
  const result = Number(leftText) * Number(rightText)
  if (!Number.isFinite(result) || result <= 0) return ''
  return result.toFixed(scale)
}

function formatLimitPrice(value: string | number) {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(4) : String(value)
}

function instrumentOptionLabel(instrument: SecurityInstrument) {
  return `${instrument.symbol} · ${instrument.instrumentName} · ${instrument.exchange}`
}

function instrumentText(instrumentId: number, field: InstrumentTextField) {
  const instrument = instruments.value.find((item) => item.id === instrumentId)
  if (!instrument) return `标的 ID: ${instrumentId}`
  return field === 'symbol' ? instrument.symbol : instrument.instrumentName
}

function orderAmount(order: SimulatedOrder) {
  return multiplyDecimal(order.requestedQuantity, order.limitPrice, 4)
}

function tradeForOrder(orderId: number) {
  return tradesByOrderId.value.get(orderId)
}

function sideLabel(side: OrderSide) {
  const labels: Record<OrderSide, string> = {
    BUY: '买入',
    SELL: '卖出',
  }
  return labels[side]
}

function orderStatusLabel(status: OrderStatus) {
  const labels: Record<OrderStatus, string> = {
    OPEN: '待成交',
    REJECTED: '已拒绝',
    FILLED: '已成交',
    CANCELED: '已取消',
  }
  return labels[status]
}

function orderStatusTag(status: OrderStatus): TagProps['type'] {
  const tags: Record<OrderStatus, TagProps['type']> = {
    OPEN: 'primary',
    REJECTED: 'danger',
    FILLED: 'success',
    CANCELED: 'info',
  }
  return tags[status]
}

function isActionLoading(orderId: number, action: OrderAction) {
  return activeActionOrderId.value === orderId && activeAction.value === action
}

function isOrderActionDisabled(orderId: number) {
  return activeActionOrderId.value !== null && activeActionOrderId.value !== orderId
}

function assetTypeLabel(type: AssetType) {
  const labels: Record<AssetType, string> = {
    STOCK: '股票',
    ETF: 'ETF',
    INDEX: '指数',
  }
  return labels[type]
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}
</script>

<style scoped>
.estimate-box span,
.position-snapshot span,
.table-heading span {
  color: var(--qg-text-muted);
  font-size: 13px;
}

.selection-empty {
  display: grid;
  min-height: 230px;
  place-items: center;
  padding: 32px;
  text-align: center;
}

.selection-empty div {
  max-width: 460px;
}

.selection-empty h2 {
  margin: 0;
  font-size: 20px;
}

.selection-empty p {
  margin: 10px 0 20px;
  color: var(--qg-text-muted);
  line-height: 1.7;
}

.work-grid {
  display: grid;
  grid-template-columns: minmax(380px, 0.9fr) minmax(420px, 1.1fr);
  gap: 16px;
  align-items: stretch;
}

.order-form-panel,
.market-panel,
.section-body {
  min-width: 0;
}

.order-form-panel,
.market-panel {
  height: 100%;
  padding: 14px;
}

.section-body {
  padding: 16px;
}

.side-stack {
  display: grid;
  gap: 16px;
  height: 100%;
  min-width: 0;
}

.market-panel {
  display: flex;
  flex-direction: column;
}

.panel-heading,
.table-heading {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.panel-heading h2,
.table-heading h2 {
  margin: 0;
  font-size: 18px;
}

.panel-heading {
  margin-bottom: 12px;
}

.chart-toolbar {
  justify-content: flex-start;
  width: 100%;
}

.chart-toolbar :deep(.el-radio-group) {
  flex-wrap: nowrap;
}

.order-form {
  display: grid;
  gap: 2px;
}

.order-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.order-input-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.full-width {
  width: 100%;
}

.position-snapshot {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
  background: var(--qg-subtle);
}

.snapshot-grid div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 10px;
}

.position-snapshot {
  gap: 0;
  margin-bottom: 12px;
  padding: 10px;
}

.position-snapshot span {
  font-size: 11px;
}

.position-snapshot strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-group {
  width: 100%;
}

.estimate-box {
  display: grid;
  gap: 4px;
  margin: 0 0 12px;
  padding: 10px;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
  background: var(--qg-subtle);
}

.estimate-box strong {
  font-size: 20px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.market-panel {
  display: grid;
  gap: 12px;
}

.selected-candle {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
  background: var(--qg-subtle);
}

.selected-candle div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.selected-candle span {
  color: var(--qg-text-muted);
  font-size: 13px;
}

.market-panel :deep(.chart) {
  height: 420px;
  min-height: 320px;
}

.muted {
  color: var(--qg-text-muted);
}

.order-security {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.order-security strong,
.order-security span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-security span {
  color: var(--qg-text-muted);
  font-size: 12px;
}

.trade-summary {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.trade-summary strong {
  font-size: 13px;
  white-space: nowrap;
}

.trade-summary span {
  overflow: hidden;
  color: var(--qg-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-table :deep(.cell) {
  padding-right: 8px;
  padding-left: 8px;
}

.order-table :deep(.el-table__cell) {
  padding-top: 7px;
  padding-bottom: 7px;
}

@media (max-width: 1180px) {
  .work-grid {
    grid-template-columns: 1fr;
  }

  .order-form-panel,
  .market-panel {
    height: auto;
  }
}

@media (max-width: 680px) {
  .selected-candle {
    grid-template-columns: 1fr;
  }

  .snapshot-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .order-input-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .selected-candle {
    display: grid;
    align-items: stretch;
  }
}
</style>
