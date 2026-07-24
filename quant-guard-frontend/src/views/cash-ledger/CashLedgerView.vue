<template>
  <section class="page">
    <PageHeader title="资金流水" description="按当前账户读取本地模拟资金流水；组合筛选在前端完成。">
      <template #actions>
        <el-space>
          <el-button :loading="loading" :disabled="!selectedAccountId" @click="loadPageData">
            刷新
          </el-button>
          <el-button @click="router.push('/accounts')">选择账户</el-button>
          <el-button @click="router.push('/portfolios')">选择组合</el-button>
        </el-space>
      </template>
    </PageHeader>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-alert
      v-if="!selectedAccountId"
      title="请先选择账户或投资组合"
      description="资金流水接口按账户读取；选择投资组合后也会自动恢复对应账户。"
      type="info"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-space>
          <el-button type="primary" @click="router.push('/accounts')">前往模拟账户</el-button>
          <el-button @click="router.push('/portfolios')">前往投资组合</el-button>
        </el-space>
      </template>
    </el-alert>

    <div class="surface context-panel">
      <div class="context-item">
        <span>当前账户</span>
        <strong>{{ currentAccountName }}</strong>
      </div>
      <div class="context-item">
        <span>账户 ID</span>
        <strong>{{ selectedAccountId ?? '未选择' }}</strong>
      </div>
      <div class="context-item">
        <span>当前组合</span>
        <strong>{{ currentPortfolioName }}</strong>
      </div>
      <div class="context-item">
        <span>组合 ID</span>
        <strong>{{ selectedPortfolioId ?? '未选择' }}</strong>
      </div>
      <div class="context-item">
        <span>可用资金</span>
        <strong><MoneyText :value="currentAccount?.availableCash" /></strong>
      </div>
      <div class="context-item">
        <span>冻结资金</span>
        <strong><MoneyText :value="currentAccount?.frozenCash" /></strong>
      </div>
    </div>

    <div v-if="selectedAccountId" class="surface filter-panel">
      <div class="panel-heading">
        <h2>筛选</h2>
        <span>筛选和汇总基于当前账户已经加载的流水记录。</span>
      </div>
      <div class="filters">
        <el-select v-model="filters.entryType" class="filter-control" placeholder="全部流水类型">
          <el-option label="全部流水类型" value="ALL" />
          <el-option
            v-for="type in entryTypes"
            :key="type"
            :label="ledgerTypeLabel(type)"
            :value="type"
          />
        </el-select>
        <el-select v-model="filters.portfolioScope" class="filter-control">
          <el-option label="全部组合" value="ALL" />
          <el-option :disabled="!selectedPortfolioId" label="当前组合" value="CURRENT" />
        </el-select>
        <el-input
          v-model.trim="filters.keyword"
          class="keyword-control"
          clearable
          placeholder="搜索订单编号、成交编号或描述"
        />
      </div>
      <p class="filter-note">
        资金流水接口返回当前账户全部流水；当前组合筛选在前端基于 portfolioId 完成。
      </p>
    </div>

    <div v-if="selectedAccountId" class="summary-grid">
      <div class="surface summary-card">
        <span>流水记录数量</span>
        <strong>{{ filteredLedger.length }}</strong>
      </div>
      <div class="surface summary-card">
        <span>可用资金变动合计</span>
        <strong>{{ signedMoneyText(summary.availableCashDelta, 'available') }}</strong>
      </div>
      <div class="surface summary-card">
        <span>冻结资金变动合计</span>
        <strong>{{ signedMoneyText(summary.frozenCashDelta, 'frozen') }}</strong>
      </div>
    </div>

    <el-alert
      v-if="selectedAccountId"
      title="这里展示的是现金变动汇总，不是收益率、投资收益或盈亏。"
      type="info"
      show-icon
      :closable="false"
    />

    <div v-if="selectedAccountId" class="surface section-body">
      <div class="table-heading">
        <h2>流水列表</h2>
        <span>当前显示 {{ filteredLedger.length }} / {{ ledgerEntries.length }} 条</span>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredLedger"
        row-key="id"
        empty-text="当前账户暂无资金流水"
      >
        <el-table-column prop="id" label="流水 ID" width="100" />
        <el-table-column label="流水类型" min-width="180" show-overflow-tooltip>
          <template #default="{ row }: { row: CashLedgerEntry }">
            {{ ledgerTypeLabel(row.entryType) }}
          </template>
        </el-table-column>
        <el-table-column label="投资组合" min-width="180" show-overflow-tooltip>
          <template #default="{ row }: { row: CashLedgerEntry }">
            {{ portfolioName(row.portfolioId) }}
          </template>
        </el-table-column>
        <el-table-column label="订单号" min-width="230" show-overflow-tooltip>
          <template #default="{ row }: { row: CashLedgerEntry }">
            {{ orderNoText(row.orderId) }}
          </template>
        </el-table-column>
        <el-table-column label="成交号" min-width="230" show-overflow-tooltip>
          <template #default="{ row }: { row: CashLedgerEntry }">
            {{ tradeNoText(row.tradeId) }}
          </template>
        </el-table-column>
        <el-table-column label="证券信息" min-width="220" show-overflow-tooltip>
          <template #default="{ row }: { row: CashLedgerEntry }">
            {{ instrumentTextByOrder(row.orderId) }}
          </template>
        </el-table-column>
        <el-table-column label="可用资金变动" min-width="190">
          <template #default="{ row }: { row: CashLedgerEntry }">
            {{ signedMoneyText(row.availableCashDelta, 'available') }}
          </template>
        </el-table-column>
        <el-table-column label="冻结资金变动" min-width="190">
          <template #default="{ row }: { row: CashLedgerEntry }">
            {{ signedMoneyText(row.frozenCashDelta, 'frozen') }}
          </template>
        </el-table-column>
        <el-table-column label="变动后可用资金" min-width="150" align="right">
          <template #default="{ row }: { row: CashLedgerEntry }">
            <MoneyText :value="row.availableCashAfter" />
          </template>
        </el-table-column>
        <el-table-column label="变动后冻结资金" min-width="150" align="right">
          <template #default="{ row }: { row: CashLedgerEntry }">
            <MoneyText :value="row.frozenCashAfter" />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }: { row: CashLedgerEntry }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="详情" width="100" fixed="right">
          <template #default="{ row }: { row: CashLedgerEntry }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="detailVisible" title="资金流水详情" size="540px">
      <div v-if="selectedLedger" class="detail-body">
        <div class="detail-grid">
          <span>流水 ID</span>
          <strong>{{ selectedLedger.id }}</strong>
          <span>账户</span>
          <strong>{{ accountName(selectedLedger.accountId) }}</strong>
          <span>投资组合</span>
          <strong>{{ portfolioName(selectedLedger.portfolioId) }}</strong>
          <span>订单</span>
          <strong>{{ orderNoText(selectedLedger.orderId) }}</strong>
          <span>成交</span>
          <strong>{{ tradeNoText(selectedLedger.tradeId) }}</strong>
          <span>流水类型</span>
          <strong>{{ ledgerTypeLabel(selectedLedger.entryType) }}</strong>
          <span>类型说明</span>
          <strong>{{ ledgerTypeDescription(selectedLedger.entryType) }}</strong>
          <span>可用资金变化</span>
          <strong>{{ signedMoneyText(selectedLedger.availableCashDelta, 'available') }}</strong>
          <span>冻结资金变化</span>
          <strong>{{ signedMoneyText(selectedLedger.frozenCashDelta, 'frozen') }}</strong>
          <span>变动后可用资金</span>
          <strong><MoneyText :value="selectedLedger.availableCashAfter" /></strong>
          <span>变动后冻结资金</span>
          <strong><MoneyText :value="selectedLedger.frozenCashAfter" /></strong>
          <span>描述</span>
          <strong>{{ selectedLedger.description }}</strong>
          <span>创建时间</span>
          <strong>{{ formatDateTime(selectedLedger.createdAt) }}</strong>
        </div>
        <el-alert
          title="这是 QuantGuard 本地模拟账户流水，不是银行或证券账户的真实资金流水。"
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
import { listCashLedgerByAccountId } from '@/api/cashLedger'
import { listInstruments } from '@/api/instrument'
import { listOrders } from '@/api/order'
import { listPortfolios } from '@/api/portfolio'
import { listTradesByPortfolioId } from '@/api/trade'
import { usePortfolioStore } from '@/stores/portfolio'
import type { SimulationAccount } from '@/types/account'
import type { CashLedgerEntry, CashLedgerEntryType } from '@/types/cashLedger'
import type { SecurityInstrument } from '@/types/instrument'
import type { SimulatedOrder } from '@/types/order'
import type { InvestmentPortfolio } from '@/types/portfolio'
import type { SimulatedTrade } from '@/types/trade'

type LedgerTypeFilter = CashLedgerEntryType | 'ALL'
type PortfolioScopeFilter = 'ALL' | 'CURRENT'
type CashKind = 'available' | 'frozen'

const router = useRouter()
const portfolioStore = usePortfolioStore()
const entryTypes: CashLedgerEntryType[] = [
  'BUY_ORDER_FREEZE',
  'BUY_CANCEL_RELEASE',
  'BUY_SETTLEMENT',
  'SELL_PROCEEDS',
]

const accounts = ref<SimulationAccount[]>([])
const portfolios = ref<InvestmentPortfolio[]>([])
const instruments = ref<SecurityInstrument[]>([])
const orders = ref<SimulatedOrder[]>([])
const trades = ref<SimulatedTrade[]>([])
const ledgerEntries = ref<CashLedgerEntry[]>([])
const loading = ref(false)
const errorMessage = ref('')
const detailVisible = ref(false)
const selectedLedger = ref<CashLedgerEntry | null>(null)
let requestSeq = 0

const filters = reactive<{
  entryType: LedgerTypeFilter
  portfolioScope: PortfolioScopeFilter
  keyword: string
}>({
  entryType: 'ALL',
  portfolioScope: 'CURRENT',
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
const filteredLedger = computed(() => {
  const keyword = filters.keyword.toLowerCase()
  const currentPortfolioId = selectedPortfolioId.value

  return ledgerEntries.value.filter((entry) => {
    if (filters.entryType !== 'ALL' && entry.entryType !== filters.entryType) return false
    if (
      filters.portfolioScope === 'CURRENT' &&
      currentPortfolioId !== null &&
      entry.portfolioId !== currentPortfolioId
    ) {
      return false
    }
    if (!keyword) return true

    return [orderNoText(entry.orderId), tradeNoText(entry.tradeId), entry.description]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword))
  })
})
const summary = computed(() => ({
  availableCashDelta: sumDecimal(filteredLedger.value.map((entry) => entry.availableCashDelta)),
  frozenCashDelta: sumDecimal(filteredLedger.value.map((entry) => entry.frozenCashDelta)),
}))

onMounted(() => {
  void loadPageData()
})

watch(
  () => [portfolioStore.selectedAccountId, portfolioStore.selectedPortfolioId],
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
  ledgerEntries.value = []
  selectedLedger.value = null
  detailVisible.value = false

  try {
    const [accountRows, portfolioRows, instrumentRows, orderRows] = await Promise.all([
      listAccounts(),
      listPortfolios(),
      listInstruments(),
      listOrders(),
    ])
    if (seq !== requestSeq) return

    accounts.value = accountRows
    portfolios.value = portfolioRows
    instruments.value = instrumentRows
    orders.value = orderRows
    reconcileSelection()
    normalizePortfolioScope()

    const accountId = selectedAccountId.value
    if (!accountId) return

    const portfolioId = selectedPortfolioId.value
    const [ledgerRows, tradeRows] = await Promise.all([
      listCashLedgerByAccountId(accountId),
      portfolioId ? listTradesByPortfolioId(portfolioId) : Promise.resolve([]),
    ])
    if (seq !== requestSeq) return
    ledgerEntries.value = ledgerRows
    trades.value = tradeRows
  } catch (error) {
    if (seq !== requestSeq) return
    errorMessage.value = errorToMessage(error, '资金流水加载失败，请确认后端服务是否已启动。')
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

function normalizePortfolioScope() {
  if (!selectedPortfolioId.value) {
    filters.portfolioScope = 'ALL'
  }
}

function openDetail(entry: CashLedgerEntry) {
  selectedLedger.value = entry
  detailVisible.value = true
}

function ledgerTypeLabel(type: CashLedgerEntryType) {
  const labels: Record<CashLedgerEntryType, string> = {
    BUY_ORDER_FREEZE: '买入冻结',
    BUY_CANCEL_RELEASE: '买入取消释放',
    BUY_SETTLEMENT: '买入结算',
    SELL_PROCEEDS: '卖出入账',
  }
  return labels[type]
}

function ledgerTypeDescription(type: CashLedgerEntryType) {
  const descriptions: Record<CashLedgerEntryType, string> = {
    BUY_ORDER_FREEZE: '买入委托创建后冻结账户可用资金',
    BUY_CANCEL_RELEASE: '买入委托取消后释放冻结资金',
    BUY_SETTLEMENT: '买入委托成交后消耗冻结资金',
    SELL_PROCEEDS: '卖出委托成交后增加账户可用资金',
  }
  return descriptions[type]
}

function signedMoneyText(value: string | number, kind: CashKind) {
  const decimal = parseDecimal(String(value))
  if (!decimal || decimal.units === 0n) return `0.0000（${cashKindLabel(kind)}无变化）`

  const sign = decimal.units > 0n ? '+' : '-'
  const direction = decimal.units > 0n ? '增加' : '减少'
  return `${sign}${formatDecimalUnits(absBigInt(decimal.units), decimal.scale, 4)}（${cashKindLabel(kind)}${direction}）`
}

function cashKindLabel(kind: CashKind) {
  return kind === 'available' ? '可用资金' : '冻结资金'
}

function orderNoText(orderId: number | null) {
  if (orderId === null) return '—'
  return orders.value.find((order) => order.id === orderId)?.orderNo ?? `订单 ID: ${orderId}`
}

function tradeNoText(tradeId: number | null) {
  if (tradeId === null) return '—'
  return trades.value.find((trade) => trade.id === tradeId)?.tradeNo ?? `成交 ID: ${tradeId}`
}

function instrumentTextByOrder(orderId: number | null) {
  if (orderId === null) return '—'
  const order = orders.value.find((item) => item.id === orderId)
  if (!order) return '—'
  const instrument = instruments.value.find((item) => item.id === order.instrumentId)
  if (!instrument) return `标的 ID: ${order.instrumentId}`
  return `${instrument.symbol} · ${instrument.instrumentName}`
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

function errorToMessage(error: unknown, fallback: string) {
  if (error instanceof AxiosError) {
    return error.response?.data?.message || fallback
  }
  if (error instanceof Error) return error.message
  return fallback
}

function sumDecimal(values: Array<string | number>) {
  const parsedValues = values
    .map((value) => parseDecimal(String(value)))
    .filter((value): value is ParsedDecimal => value !== null)
  const scale = Math.max(4, ...parsedValues.map((value) => value.scale))
  const units = parsedValues.reduce(
    (total, value) => total + value.units * 10n ** BigInt(scale - value.scale),
    0n,
  )
  return formatDecimalUnits(units, scale, 4)
}

interface ParsedDecimal {
  units: bigint
  scale: number
}

function parseDecimal(value: string): ParsedDecimal | null {
  if (!/^-?\d+(\.\d+)?$/.test(value)) return null
  const sign = value.startsWith('-') ? -1n : 1n
  const unsigned = value.replace('-', '')
  const [integerPart, decimalPart = ''] = unsigned.split('.')
  return {
    units: BigInt(`${integerPart}${decimalPart}`) * sign,
    scale: decimalPart.length,
  }
}

function formatDecimalUnits(units: bigint, scale: number, fractionDigits: number) {
  const sign = units < 0n ? '-' : ''
  const absolute = absBigInt(units)
  const normalized =
    scale >= fractionDigits
      ? absolute / 10n ** BigInt(scale - fractionDigits)
      : absolute * 10n ** BigInt(fractionDigits - scale)
  const text = normalized.toString().padStart(fractionDigits + 1, '0')
  const integerPart = text.slice(0, -fractionDigits)
  const decimalPart = text.slice(-fractionDigits)
  return `${sign}${integerPart}.${decimalPart}`
}

function absBigInt(value: bigint) {
  return value < 0n ? -value : value
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
  grid-template-columns: repeat(6, minmax(0, 1fr));
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
.table-heading span,
.summary-card span,
.filter-note {
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

.filters,
.summary-grid {
  display: grid;
  gap: 12px;
}

.filters {
  grid-template-columns: 180px 160px minmax(280px, 1fr);
}

.summary-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.summary-card {
  display: grid;
  gap: 8px;
  padding: 16px;
}

.summary-card strong {
  font-size: 18px;
}

.filter-control,
.keyword-control {
  width: 100%;
}

.filter-note {
  margin: 12px 0 0;
}

.detail-body {
  display: grid;
  gap: 18px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
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
  .filters,
  .summary-grid {
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
