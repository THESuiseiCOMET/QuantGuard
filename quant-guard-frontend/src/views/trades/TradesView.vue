<template>
  <section class="page">
    <PageHeader
      title="成交记录"
      description="按当前投资组合读取真实模拟成交；成交价格来自订单限价。"
    >
      <template #actions>
        <el-space>
          <el-button :loading="loading" :disabled="!selectedPortfolioId" @click="loadPageData">
            刷新
          </el-button>
          <el-button @click="router.push('/portfolios')">选择组合</el-button>
        </el-space>
      </template>
    </PageHeader>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-alert
      v-if="!selectedPortfolioId"
      title="请先选择投资组合"
      description="成交记录按当前投资组合读取；未选择组合时不会请求成交接口。"
      type="info"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button type="primary" @click="router.push('/portfolios')">前往投资组合</el-button>
      </template>
    </el-alert>

    <div class="surface context-panel">
      <div class="context-item">
        <span>当前账户</span>
        <strong>{{ currentAccountName }}</strong>
      </div>
      <div class="context-item">
        <span>当前投资组合</span>
        <strong>{{ currentPortfolioName }}</strong>
      </div>
      <div class="context-item">
        <span>账户 ID</span>
        <strong>{{ selectedAccountId ?? '未选择' }}</strong>
      </div>
      <div class="context-item">
        <span>组合 ID</span>
        <strong>{{ selectedPortfolioId ?? '未选择' }}</strong>
      </div>
      <div class="context-item">
        <span>成交数量</span>
        <strong>{{ trades.length }}</strong>
      </div>
    </div>

    <div v-if="selectedPortfolioId" class="surface filter-panel">
      <div class="panel-heading">
        <h2>筛选</h2>
        <span>筛选仅作用于当前已加载的成交记录。</span>
      </div>
      <div class="filters">
        <el-select v-model="filters.side" class="filter-control" placeholder="全部方向">
          <el-option label="全部方向" value="ALL" />
          <el-option label="买入" value="BUY" />
          <el-option label="卖出" value="SELL" />
        </el-select>
        <el-select
          v-model="filters.instrumentId"
          class="filter-control"
          clearable
          filterable
          placeholder="全部证券标的"
        >
          <el-option
            v-for="instrument in instruments"
            :key="instrument.id"
            :label="instrumentOptionLabel(instrument)"
            :value="instrument.id"
          />
        </el-select>
        <el-input
          v-model.trim="filters.keyword"
          class="keyword-control"
          clearable
          placeholder="搜索成交编号、订单编号、证券代码或证券名称"
        />
      </div>
    </div>

    <div v-if="selectedPortfolioId" class="surface section-body">
      <div class="table-heading">
        <h2>成交列表</h2>
        <span>当前显示 {{ filteredTrades.length }} / {{ trades.length }} 条</span>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredTrades"
        row-key="id"
        empty-text="当前投资组合暂无成交记录"
      >
        <el-table-column prop="tradeNo" label="成交编号" min-width="230" show-overflow-tooltip />
        <el-table-column label="关联订单号" min-width="230" show-overflow-tooltip>
          <template #default="{ row }: { row: SimulatedTrade }">
            {{ orderNoText(row.orderId) }}
          </template>
        </el-table-column>
        <el-table-column label="证券代码" min-width="140">
          <template #default="{ row }: { row: SimulatedTrade }">
            {{ instrumentText(row.instrumentId, 'symbol') }}
          </template>
        </el-table-column>
        <el-table-column label="证券名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }: { row: SimulatedTrade }">
            {{ instrumentText(row.instrumentId, 'name') }}
          </template>
        </el-table-column>
        <el-table-column label="方向" width="110">
          <template #default="{ row }: { row: SimulatedTrade }">
            <el-tag :type="row.side === 'BUY' ? 'success' : 'warning'" effect="plain">
              {{ sideLabel(row.side) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成交数量" min-width="130" align="right">
          <template #default="{ row }: { row: SimulatedTrade }">
            <MoneyText :value="row.quantity" />
          </template>
        </el-table-column>
        <el-table-column label="执行价格" min-width="130" align="right">
          <template #default="{ row }: { row: SimulatedTrade }">
            <MoneyText :value="row.executionPrice" />
          </template>
        </el-table-column>
        <el-table-column label="成交金额" min-width="140" align="right">
          <template #default="{ row }: { row: SimulatedTrade }">
            <MoneyText :value="row.tradeAmount" />
          </template>
        </el-table-column>
        <el-table-column label="执行时间" min-width="180">
          <template #default="{ row }: { row: SimulatedTrade }">
            {{ formatDateTime(row.executedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }: { row: SimulatedTrade }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }: { row: SimulatedTrade }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="detailVisible" title="成交详情" size="520px">
      <div v-if="selectedTrade" class="detail-body">
        <div class="detail-grid">
          <span>成交编号</span>
          <strong>{{ selectedTrade.tradeNo }}</strong>
          <span>成交 ID</span>
          <strong>{{ selectedTrade.id }}</strong>
          <span>订单编号</span>
          <strong>{{ orderNoText(selectedTrade.orderId) }}</strong>
          <span>订单 ID</span>
          <strong>{{ selectedTrade.orderId }}</strong>
          <span>账户</span>
          <strong>{{ accountName(selectedTrade.accountId) }}</strong>
          <span>投资组合</span>
          <strong>{{ portfolioName(selectedTrade.portfolioId) }}</strong>
          <span>证券代码</span>
          <strong>{{ instrumentText(selectedTrade.instrumentId, 'symbol') }}</strong>
          <span>证券名称</span>
          <strong>{{ instrumentText(selectedTrade.instrumentId, 'name') }}</strong>
          <span>买卖方向</span>
          <strong>{{ sideLabel(selectedTrade.side) }}</strong>
          <span>数量</span>
          <strong><MoneyText :value="selectedTrade.quantity" /></strong>
          <span>执行价格</span>
          <strong><MoneyText :value="selectedTrade.executionPrice" /></strong>
          <span>成交金额</span>
          <strong><MoneyText :value="selectedTrade.tradeAmount" /></strong>
          <span>执行时间</span>
          <strong>{{ formatDateTime(selectedTrade.executedAt) }}</strong>
          <span>创建时间</span>
          <strong>{{ formatDateTime(selectedTrade.createdAt) }}</strong>
        </div>
        <el-alert
          title="成交价格来源于模拟订单限价，不代表真实市场成交价格。QuantGuard 不连接真实券商，也不执行真实证券交易。"
          type="info"
          show-icon
          :closable="false"
        />
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { AxiosError } from 'axios'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import { listAccounts } from '@/api/account'
import { listInstruments } from '@/api/instrument'
import { listOrdersByPortfolioId } from '@/api/order'
import { listPortfolios } from '@/api/portfolio'
import { listTradesByPortfolioId } from '@/api/trade'
import { usePortfolioStore } from '@/stores/portfolio'
import type { SimulationAccount } from '@/types/account'
import type { SecurityInstrument } from '@/types/instrument'
import type { OrderSide, SimulatedOrder } from '@/types/order'
import type { InvestmentPortfolio } from '@/types/portfolio'
import type { SimulatedTrade } from '@/types/trade'

type InstrumentTextField = 'symbol' | 'name'
type SideFilter = OrderSide | 'ALL'

const router = useRouter()
const portfolioStore = usePortfolioStore()

const accounts = ref<SimulationAccount[]>([])
const portfolios = ref<InvestmentPortfolio[]>([])
const instruments = ref<SecurityInstrument[]>([])
const orders = ref<SimulatedOrder[]>([])
const trades = ref<SimulatedTrade[]>([])
const loading = ref(false)
const errorMessage = ref('')
const detailVisible = ref(false)
const selectedTrade = ref<SimulatedTrade | null>(null)
let requestSeq = 0

const filters = reactive<{
  side: SideFilter
  instrumentId: number | null
  keyword: string
}>({
  side: 'ALL',
  instrumentId: null,
  keyword: '',
})

const selectedAccountId = computed(() => portfolioStore.selectedAccountId)
const selectedPortfolioId = computed(() => portfolioStore.selectedPortfolioId)
const currentPortfolio = computed(() =>
  portfolios.value.find((portfolio) => portfolio.id === selectedPortfolioId.value),
)
const currentAccount = computed(() =>
  accounts.value.find((account) => account.id === selectedAccountId.value),
)
const currentPortfolioName = computed(() => currentPortfolio.value?.portfolioName ?? '未选择')
const currentAccountName = computed(() => currentAccount.value?.accountName ?? '未选择')
const filteredTrades = computed(() => {
  const keyword = filters.keyword.toLowerCase()

  return trades.value.filter((trade) => {
    if (filters.side !== 'ALL' && trade.side !== filters.side) return false
    if (filters.instrumentId !== null && trade.instrumentId !== filters.instrumentId) return false
    if (!keyword) return true

    const instrument = instruments.value.find((item) => item.id === trade.instrumentId)
    const order = orders.value.find((item) => item.id === trade.orderId)
    return [trade.tradeNo, order?.orderNo, instrument?.symbol, instrument?.instrumentName]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword))
  })
})

onMounted(() => {
  void loadPageData()
})

watch(
  () => portfolioStore.selectedPortfolioId,
  () => {
    void loadPageData()
  },
)

async function loadPageData() {
  const seq = ++requestSeq
  loading.value = true
  errorMessage.value = ''
  orders.value = []
  trades.value = []
  selectedTrade.value = null
  detailVisible.value = false

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

    const portfolioId = selectedPortfolioId.value
    if (!portfolioId) return

    const [orderRows, tradeRows] = await Promise.all([
      listOrdersByPortfolioId(portfolioId),
      listTradesByPortfolioId(portfolioId),
    ])
    if (seq !== requestSeq) return
    orders.value = orderRows
    trades.value = tradeRows
  } catch (error) {
    if (seq !== requestSeq) return
    errorMessage.value = errorToMessage(error, '成交记录加载失败，请确认后端服务是否已启动。')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function reconcileSelection() {
  const selectedAccountExists =
    portfolioStore.selectedAccountId === null ||
    accounts.value.some((account) => account.id === portfolioStore.selectedAccountId)

  if (!selectedAccountExists) {
    portfolioStore.selectAccount(null)
  }

  const selectedPortfolio = portfolios.value.find(
    (portfolio) => portfolio.id === portfolioStore.selectedPortfolioId,
  )

  if (!selectedPortfolio) {
    portfolioStore.selectPortfolio(null)
    return
  }

  if (portfolioStore.selectedAccountId !== selectedPortfolio.accountId) {
    portfolioStore.selectAccount(selectedPortfolio.accountId)
  }
}

function openDetail(trade: SimulatedTrade) {
  selectedTrade.value = trade
  detailVisible.value = true
}

function instrumentOptionLabel(instrument: SecurityInstrument) {
  return `${instrument.symbol} · ${instrument.instrumentName} · ${instrument.exchange}`
}

function instrumentText(instrumentId: number, field: InstrumentTextField) {
  const instrument = instruments.value.find((item) => item.id === instrumentId)
  if (!instrument) return `标的 ID: ${instrumentId}`
  return field === 'symbol' ? instrument.symbol : instrument.instrumentName
}

function orderNoText(orderId: number) {
  return orders.value.find((order) => order.id === orderId)?.orderNo ?? `订单 ID: ${orderId}`
}

function accountName(accountId: number) {
  return (
    accounts.value.find((account) => account.id === accountId)?.accountName ??
    `账户 ID: ${accountId}`
  )
}

function portfolioName(portfolioId: number) {
  return (
    portfolios.value.find((portfolio) => portfolio.id === portfolioId)?.portfolioName ??
    `组合 ID: ${portfolioId}`
  )
}

function sideLabel(side: OrderSide) {
  const labels: Record<OrderSide, string> = {
    BUY: '买入',
    SELL: '卖出',
  }
  return labels[side]
}

function errorToMessage(error: unknown, fallback: string) {
  if (error instanceof AxiosError) {
    return error.response?.data?.message || fallback
  }
  if (error instanceof Error) return error.message
  return fallback
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
.context-panel {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.context-item {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 16px;
  border-right: 1px solid var(--qg-border);
}

.context-item:last-child {
  border-right: 0;
}

.context-item span,
.panel-heading span,
.table-heading span {
  color: var(--qg-text-muted);
  font-size: 13px;
}

.context-item strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-panel,
.section-body {
  padding: 16px;
}

.panel-heading,
.table-heading {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-heading h2,
.table-heading h2 {
  margin: 0;
  font-size: 18px;
}

.filters {
  display: grid;
  grid-template-columns: 160px minmax(220px, 280px) minmax(280px, 1fr);
  gap: 12px;
}

.filter-control,
.keyword-control {
  width: 100%;
}

.detail-body {
  display: grid;
  gap: 18px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 12px 16px;
}

.detail-grid span {
  color: var(--qg-text-muted);
}

.detail-grid strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 1100px) {
  .context-panel,
  .filters {
    grid-template-columns: 1fr;
  }

  .context-item {
    border-right: 0;
    border-bottom: 1px solid var(--qg-border);
  }

  .context-item:last-child {
    border-bottom: 0;
  }
}
</style>
