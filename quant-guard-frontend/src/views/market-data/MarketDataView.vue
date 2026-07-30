<template>
  <section class="page">
    <PageHeader v-if="!embedded" title="行情中心" />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />

    <div class="market-layout">
      <aside class="surface instrument-panel">
        <div class="panel-title">
          <div class="panel-title-copy">
            <span>证券列表</span>
            <small>{{ filteredInstruments.length }} 只</small>
          </div>
          <el-tooltip content="添加证券" placement="top">
            <el-button
              class="panel-icon-button"
              :icon="Plus"
              circle
              aria-label="添加证券"
              @click="openImportDialog"
            />
          </el-tooltip>
        </div>
        <div class="instrument-search-row">
          <el-input
            v-model="filters.keyword"
            :prefix-icon="Search"
            placeholder="搜索代码或名称"
            clearable
          />
          <el-tooltip content="筛选证券" placement="top">
            <el-button
              class="filter-button"
              :class="{ active: filters.exchange || filters.status }"
              :icon="Filter"
              aria-label="筛选证券"
              @click="filtersOpen = !filtersOpen"
            />
          </el-tooltip>
        </div>
        <div v-if="filtersOpen" class="filter-row">
          <el-select v-model="filters.exchange" placeholder="交易所" clearable>
            <el-option label="上交所 SSE" value="SSE" />
            <el-option label="深交所 SZSE" value="SZSE" />
          </el-select>
          <el-select v-model="filters.status" placeholder="状态" clearable>
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </div>

        <div v-if="filteredInstruments.length === 0" class="instrument-empty">
          <strong>暂无证券</strong>
          <span>请搜索并添加证券</span>
        </div>

        <el-scrollbar class="instrument-scroll">
          <div
            v-for="instrument in filteredInstruments"
            :key="instrument.id"
            class="instrument-item"
            :class="{ active: instrument.id === selectedInstrumentId }"
          >
            <button
              class="instrument-select"
              type="button"
              @click="selectInstrument(instrument.id)"
            >
              <span class="instrument-identity">
                <strong>{{ instrumentDisplayName(instrument) }}</strong>
                <small>{{ instrumentSecondaryText(instrument) }}</small>
              </span>
              <span class="instrument-status" :class="instrument.status.toLowerCase()">
                <i aria-hidden="true"></i>
                {{ statusLabel(instrument.status) }}
              </span>
            </button>
            <div class="instrument-actions">
              <el-tooltip
                :content="
                  instrument.status === 'ACTIVE' ? '同步行情' : '停用证券需先恢复后才能同步行情'
                "
                placement="top"
              >
                <el-button
                  class="instrument-action-button"
                  text
                  circle
                  :icon="Refresh"
                  :disabled="instrument.status !== 'ACTIVE'"
                  :loading="syncingInstrumentId === instrument.id"
                  aria-label="同步行情"
                  @click="syncInstrument(instrument)"
                />
              </el-tooltip>
              <el-dropdown trigger="click" @command="handleInstrumentCommand(instrument, $event)">
                <el-button
                  class="instrument-action-button"
                  text
                  circle
                  :icon="MoreFilled"
                  aria-label="更多操作"
                />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="toggle-status">
                      {{ instrument.status === 'ACTIVE' ? '停用' : '恢复' }}
                    </el-dropdown-item>
                    <el-dropdown-item command="clear-prices">清空历史行情</el-dropdown-item>
                    <el-dropdown-item command="delete" divided class="danger-menu-item">
                      永久删除
                    </el-dropdown-item>
                    <el-dropdown-item command="force-delete" class="danger-menu-item">
                      硬删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </el-scrollbar>
      </aside>

      <main class="surface market-main">
        <header class="quote-header">
          <div class="quote-primary">
            <div class="quote-identity">
              <span>当前证券</span>
              <strong>{{ selectedInstrumentLabel }}</strong>
              <small>
                {{
                  selectedInstrument
                    ? instrumentSecondaryText(selectedInstrument)
                    : '从左侧列表选择证券'
                }}
              </small>
            </div>
            <div class="quote-price">
              <span>最新收盘价</span>
              <strong>{{ formatDecimal(summary?.latestClose) }}</strong>
            </div>
            <div class="quote-change">
              <span>最新涨跌幅</span>
              <strong :class="changeClass(summary?.latestPercentageChange)">
                {{ signed(summary?.latestPercentageChange, '%') }}
              </strong>
            </div>
            <div class="quote-actions">
              <el-button
                :icon="Refresh"
                :disabled="!selectedInstrumentId || selectedInstrument?.status !== 'ACTIVE'"
                :loading="syncing"
                title="停用证券需先恢复后才能同步行情"
                @click="syncCurrent"
              >
                同步
              </el-button>
              <el-button
                :icon="ShoppingCart"
                :disabled="
                  !selectedInstrumentId ||
                  selectedInstrument?.status !== 'ACTIVE' ||
                  selectedInstrument?.assetType === 'INDEX'
                "
                :title="
                  selectedInstrument?.assetType === 'INDEX' ? '指数仅用于查看行情，不能下单' : ''
                "
                @click="goToOrder"
              >
                下单
              </el-button>
            </div>
          </div>
          <div class="quote-meta">
            <div>
              <span>最新日期</span>
              <strong>{{ summary?.latestTradeDate ?? '暂无数据' }}</strong>
            </div>
            <div data-testid="range-high">
              <span>区间最高</span>
              <strong>{{ formatDecimal(rangeHigh) }}</strong>
            </div>
            <div data-testid="range-low">
              <span>区间最低</span>
              <strong>{{ formatDecimal(rangeLow) }}</strong>
            </div>
            <div class="quote-sync-meta">
              <span>行情数据</span>
              <strong>{{ prices.length }} 条日线</strong>
              <small>同步于 {{ formatDateTime(summary?.lastSyncAt) }}</small>
            </div>
          </div>
        </header>

        <div class="chart-toolbar">
          <div class="range-tools">
            <el-radio-group
              :model-value="displayRangeKey"
              size="small"
              @change="handleRangeSelection"
            >
              <el-radio-button value="1M">1月</el-radio-button>
              <el-radio-button value="3M">3月</el-radio-button>
              <el-radio-button value="6M">6月</el-radio-button>
              <el-radio-button value="1Y">1年</el-radio-button>
              <el-radio-button value="3Y">3年</el-radio-button>
              <el-radio-button value="ALL">全部</el-radio-button>
              <el-radio-button v-if="customRangeActive" value="CUSTOM">自定义</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <div class="chart-panel">
          <el-empty
            v-if="!selectedInstrumentId"
            :image-size="72"
            description="请选择一只证券查看 K 线"
          />
          <el-empty
            v-else-if="!priceLoading && prices.length === 0"
            :image-size="72"
            description="当前证券暂无历史日线，请重新同步行情。"
          >
            <el-button
              v-if="selectedInstrument?.status === 'ACTIVE'"
              :icon="Refresh"
              :loading="syncing"
              @click="syncCurrent"
            >
              同步行情
            </el-button>
          </el-empty>
          <CandlestickChart
            v-else
            v-loading="priceLoading"
            :prices="prices"
            @visible-range-change="handleVisibleRangeChange"
          />
        </div>
      </main>
    </div>

    <el-dialog v-model="dialogVisible" title="添加证券" width="min(960px, calc(100vw - 32px))">
      <el-form label-position="top">
        <el-form-item label="按代码或名称搜索">
          <el-select
            v-model="selectedCatalogCodes"
            class="full-width catalog-select"
            multiple
            filterable
            remote
            reserve-keyword
            collapse-tags
            collapse-tags-tooltip
            :remote-method="searchCatalog"
            :loading="catalogLoading"
            placeholder="输入代码或名称，例如：000688、科创50"
            :no-data-text="catalogNoDataText"
          >
            <el-option
              v-for="item in catalogResults"
              :key="item.canonicalCode"
              :label="catalogOptionLabel(item)"
              :value="item.canonicalCode"
              :disabled="item.alreadyExists"
            >
              <div class="catalog-option">
                <span>
                  <strong>{{ item.instrumentName }}</strong>
                  <small>
                    {{ item.canonicalCode }} · {{ exchangeLabel(item.exchange) }} ·
                    {{ assetTypeLabel(item.assetType) }}
                  </small>
                </span>
                <el-tag v-if="item.alreadyExists" size="small" type="info" effect="plain">
                  已添加
                </el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-space>
          <el-checkbox v-model="syncOnImport">导入后同步历史日线</el-checkbox>
          <el-date-picker
            v-model="importStartDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="同步开始日期"
          />
        </el-space>
      </el-form>

      <div class="dialog-actions">
        <el-button
          :disabled="selectedCatalogCodes.length === 0"
          :loading="previewing"
          @click="previewImport"
        >
          检查所选证券
        </el-button>
        <el-button
          type="primary"
          :disabled="validPreviewCount === 0"
          :loading="importing"
          @click="confirmImport"
        >
          添加到行情中心
        </el-button>
      </div>

      <el-table
        v-if="previewItems.length > 0"
        class="dialog-table"
        :data="previewItems"
        row-key="originalInput"
        max-height="280"
        table-layout="fixed"
      >
        <el-table-column prop="originalInput" label="原始输入" width="120" />
        <el-table-column prop="canonicalCode" label="标准代码" width="120" />
        <el-table-column prop="instrumentName" label="证券名称" min-width="160" />
        <el-table-column prop="exchange" label="交易所" width="100" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }: { row: ImportPreviewItem }">
            {{ row.assetType ? assetTypeLabel(row.assetType) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="90" />
        <el-table-column label="lotSize" width="100">
          <template #default="{ row }: { row: ImportPreviewItem }">
            {{ formatDecimal(row.lotSize) }}
          </template>
        </el-table-column>
        <el-table-column label="已存在" width="90">
          <template #default="{ row }: { row: ImportPreviewItem }">
            <el-tag :type="row.alreadyExists ? 'warning' : 'success'" effect="plain">
              {{ row.alreadyExists ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="校验" width="90">
          <template #default="{ row }: { row: ImportPreviewItem }">
            <el-tag :type="row.valid ? 'success' : 'danger'" effect="plain">
              {{ row.valid ? '通过' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="错误信息" min-width="220" show-overflow-tooltip />
      </el-table>

      <el-table
        v-if="importResults.length > 0"
        class="dialog-table"
        :data="importResults"
        row-key="canonicalCode"
        max-height="260"
        table-layout="fixed"
      >
        <el-table-column prop="canonicalCode" label="标准代码" width="120" />
        <el-table-column prop="instrumentName" label="证券名称" min-width="150" />
        <el-table-column prop="action" label="导入结果" width="100" />
        <el-table-column prop="priceSyncStatus" label="行情同步" width="110" />
        <el-table-column prop="recordsInserted" label="新增日线" width="90" />
        <el-table-column prop="recordsUpdated" label="更新日线" width="90" />
        <el-table-column prop="latestTradeDate" label="最新日期" width="120" />
        <el-table-column prop="message" label="信息" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
/* global window */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Filter, MoreFilled, Plus, Refresh, Search, ShoppingCart } from '@element-plus/icons-vue'
import { AxiosError } from 'axios'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  deleteInstrument,
  forceDeleteInstrument,
  listInstruments,
  updateInstrumentStatus,
} from '@/api/instrument'
import {
  clearInstrumentDailyPrices,
  getInstrumentDailyPriceSummary,
  importInstruments,
  listInstrumentDailyPrices,
  previewInstrumentImport,
  searchInstrumentCatalog,
  syncInstrumentDailyPrices,
} from '@/api/marketData'
import type { AssetType, Exchange, SecurityInstrument } from '@/types/instrument'
import type {
  DailyPrice,
  DailyPriceSummary,
  ImportPreviewItem,
  InstrumentCatalogItem,
  InstrumentImportResult,
} from '@/types/marketData'
import type { ApiResponse } from '@/types/api'
import CandlestickChart from './components/CandlestickChart.vue'

type RangeKey = '1M' | '3M' | '6M' | '1Y' | '3Y' | 'ALL'
type DisplayRangeKey = RangeKey | 'CUSTOM'
type VisibleRange = {
  startIndex: number
  endIndex: number
}
type ChartVisibleRange = VisibleRange & {
  userInitiated: boolean
}

const storageKey = 'quantguard.market-data.selectedInstrumentId'
const router = useRouter()

defineProps<{
  embedded?: boolean
}>()

const instruments = ref<SecurityInstrument[]>([])
const selectedInstrumentId = ref<number | null>(restoreSelectedInstrument())
const prices = ref<DailyPrice[]>([])
const summary = ref<DailyPriceSummary | null>(null)
const loading = ref(false)
const priceLoading = ref(false)
const syncing = ref(false)
const syncingInstrumentId = ref<number | null>(null)
const previewing = ref(false)
const importing = ref(false)
const catalogLoading = ref(false)
const catalogSearchAttempted = ref(false)
const catalogSearchError = ref('')
const errorMessage = ref('')
const dialogVisible = ref(false)
const filtersOpen = ref(false)
const catalogResults = ref<InstrumentCatalogItem[]>([])
const selectedCatalogCodes = ref<string[]>([])
const importStartDate = ref<string | null>(null)
const syncOnImport = ref(true)
const previewItems = ref<ImportPreviewItem[]>([])
const importResults = ref<InstrumentImportResult[]>([])
const rangeKey = ref<RangeKey>('1Y')
let activeCatalogQuery = ''
const visibleRange = ref<VisibleRange | null>(null)
const customRangeActive = ref(false)
let requestSeq = 0
let catalogSearchSeq = 0

const filters = reactive<{
  keyword: string
  exchange: '' | 'SSE' | 'SZSE'
  status: '' | 'ACTIVE' | 'DISABLED'
}>({
  keyword: '',
  exchange: '',
  status: '',
})

const filteredInstruments = computed(() =>
  instruments.value.filter((instrument) => {
    if (
      instrument.assetType !== 'STOCK' &&
      instrument.assetType !== 'ETF' &&
      instrument.assetType !== 'INDEX'
    )
      return false
    if (instrument.exchange !== 'SSE' && instrument.exchange !== 'SZSE') return false
    if (filters.exchange && instrument.exchange !== filters.exchange) return false
    if (filters.status && instrument.status !== filters.status) return false
    const keyword = filters.keyword.trim().toLowerCase()
    if (!keyword) return true
    return [instrument.symbol, instrument.instrumentName].some((value) =>
      value.toLowerCase().includes(keyword),
    )
  }),
)

const selectedInstrument = computed(
  () =>
    instruments.value.find((instrument) => instrument.id === selectedInstrumentId.value) ?? null,
)

const selectedInstrumentLabel = computed(() => {
  if (!selectedInstrument.value) return '未选择'
  const displayName = instrumentDisplayName(selectedInstrument.value)
  return displayName === selectedInstrument.value.symbol
    ? `${selectedInstrument.value.symbol} · ${selectedInstrument.value.exchange}`
    : `${displayName} · ${selectedInstrument.value.symbol}`
})

const validPreviewCount = computed(() => previewItems.value.filter((item) => item.valid).length)
const catalogNoDataText = computed(() => {
  if (catalogSearchError.value) return catalogSearchError.value
  if (catalogSearchAttempted.value) return '未找到匹配的证券'
  return '请输入代码或名称搜索'
})

const displayRangeKey = computed<DisplayRangeKey>(() =>
  customRangeActive.value ? 'CUSTOM' : rangeKey.value,
)

const visiblePrices = computed(() => {
  if (!visibleRange.value) return prices.value
  const startIndex = Math.max(0, visibleRange.value.startIndex)
  const endIndex = Math.min(prices.value.length - 1, visibleRange.value.endIndex)
  return prices.value.slice(startIndex, endIndex + 1)
})

const rangeHigh = computed(() => {
  const highs = visiblePrices.value.map((price) => Number(price.high)).filter(Number.isFinite)
  return highs.length > 0 ? Math.max(...highs) : null
})

const rangeLow = computed(() => {
  const lows = visiblePrices.value.map((price) => Number(price.low)).filter(Number.isFinite)
  return lows.length > 0 ? Math.min(...lows) : null
})

onMounted(() => {
  void loadInstruments()
})

watch(selectedInstrumentId, (value) => {
  if (value) {
    window.localStorage.setItem(storageKey, String(value))
  } else {
    window.localStorage.removeItem(storageKey)
  }
})

watch(selectedCatalogCodes, () => {
  previewItems.value = []
  importResults.value = []
})

async function loadInstruments() {
  loading.value = true
  errorMessage.value = ''

  try {
    instruments.value = await listInstruments()
    reconcileSelectedInstrument()
    await loadPrices()
  } catch {
    errorMessage.value = '行情中心加载失败，请确认后端服务是否已启动。'
  } finally {
    loading.value = false
  }
}

async function loadPrices() {
  visibleRange.value = null
  customRangeActive.value = false
  if (!selectedInstrumentId.value) {
    prices.value = []
    summary.value = null
    return
  }
  const seq = ++requestSeq
  priceLoading.value = true
  errorMessage.value = ''

  try {
    const [priceRows, priceSummary] = await Promise.all([
      listInstrumentDailyPrices(selectedInstrumentId.value, rangeParams(rangeKey.value)),
      getInstrumentDailyPriceSummary(selectedInstrumentId.value),
    ])
    if (seq !== requestSeq) return
    prices.value = priceRows
    summary.value = priceSummary
  } catch (error) {
    if (seq !== requestSeq) return
    const message = error instanceof Error ? error.message : ''
    errorMessage.value = message.includes('TUSHARE_TOKEN')
      ? '行情服务尚未配置。请在“设置”中填写 Tushare Token。'
      : '历史日线加载失败，请确认后端服务是否已启动。'
  } finally {
    if (seq === requestSeq) priceLoading.value = false
  }
}

function handleRangeSelection(value: string | number | boolean | undefined) {
  if (value === 'CUSTOM' || !isRangeKey(value)) return
  rangeKey.value = value
  void loadPrices()
}

function handleVisibleRangeChange(range: ChartVisibleRange) {
  if (prices.value.length === 0) return
  const lastIndex = prices.value.length - 1
  const startIndex = Math.min(lastIndex, Math.max(0, range.startIndex))
  const endIndex = Math.min(lastIndex, Math.max(startIndex, range.endIndex))
  const isFullRange = startIndex === 0 && endIndex === lastIndex
  visibleRange.value = isFullRange ? null : { startIndex, endIndex }
  customRangeActive.value = range.userInitiated && !isFullRange
}

function isRangeKey(value: unknown): value is RangeKey {
  return (
    value === '1M' ||
    value === '3M' ||
    value === '6M' ||
    value === '1Y' ||
    value === '3Y' ||
    value === 'ALL'
  )
}

async function previewImport() {
  const codes = selectedCatalogCodes.value
  if (codes.length === 0) {
    ElMessage.warning('请先搜索并选择证券')
    return
  }
  previewing.value = true
  previewItems.value = []
  importResults.value = []

  try {
    previewItems.value = await previewInstrumentImport({ codes })
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    errorMessage.value = message.includes('TUSHARE_TOKEN')
      ? '行情服务尚未配置。请在“设置”中填写 Tushare Token。'
      : '导入预览失败，请检查后端服务。'
  } finally {
    previewing.value = false
  }
}

function openImportDialog() {
  catalogSearchSeq += 1
  selectedCatalogCodes.value = []
  catalogResults.value = []
  catalogSearchAttempted.value = false
  catalogSearchError.value = ''
  activeCatalogQuery = ''
  catalogLoading.value = false
  previewItems.value = []
  importResults.value = []
  dialogVisible.value = true
}

async function searchCatalog(query: string) {
  const keyword = query.trim()
  if (catalogLoading.value && keyword === activeCatalogQuery) return
  const seq = ++catalogSearchSeq
  if (!keyword) {
    catalogResults.value = []
    catalogSearchAttempted.value = false
    catalogSearchError.value = ''
    activeCatalogQuery = ''
    catalogLoading.value = false
    return
  }
  activeCatalogQuery = keyword
  catalogSearchAttempted.value = true
  catalogSearchError.value = ''
  catalogLoading.value = true
  try {
    const results = await searchInstrumentCatalog(keyword, 30)
    if (seq === catalogSearchSeq) catalogResults.value = results
  } catch (error) {
    if (seq === catalogSearchSeq) {
      catalogResults.value = []
      catalogSearchError.value = catalogSearchErrorText(error)
    }
  } finally {
    if (seq === catalogSearchSeq) catalogLoading.value = false
  }
}

function catalogSearchErrorText(error: unknown) {
  const message =
    error instanceof AxiosError
      ? (error.response?.data as ApiResponse<unknown> | undefined)?.message || error.message
      : error instanceof Error
        ? error.message
        : ''
  const normalized = message.toLowerCase()
  if (normalized.includes('timed out') || normalized.includes('timeout')) {
    return '行情服务响应超时，请稍后重试'
  }
  if (
    normalized.includes('unavailable') ||
    normalized.includes('network') ||
    normalized.includes('service unavailable')
  ) {
    return '行情服务不可用，请确认 Python 行情服务已启动'
  }
  return '证券目录搜索失败，请稍后重试'
}

async function confirmImport() {
  const codes = previewItems.value
    .filter((item) => item.valid && item.canonicalCode)
    .map((item) => item.canonicalCode as string)
  if (codes.length === 0) return

  importing.value = true
  importResults.value = []

  try {
    importResults.value = await importInstruments({
      codes,
      syncDailyPrices: syncOnImport.value,
      startDate: importStartDate.value,
    })
    ElMessage.success('导入请求已完成')
    await loadInstruments()
  } finally {
    importing.value = false
  }
}

async function syncCurrent() {
  if (!selectedInstrument.value) return
  await syncInstrument(selectedInstrument.value)
}

async function syncInstrument(instrument: SecurityInstrument) {
  if (instrument.status !== 'ACTIVE') {
    ElMessage.warning('停用证券需先恢复后才能同步行情')
    return
  }
  syncing.value = true
  syncingInstrumentId.value = instrument.id

  try {
    const result = await syncInstrumentDailyPrices(instrument.id, {
      startDate: null,
      endDate: null,
      fullRefresh: false,
    })
    if (result.status === 'SUCCESS' && result.recordsReceived > 0) {
      ElMessage.success('同步完成')
    } else if (result.status === 'SUCCESS') {
      ElMessage.warning('同步完成，但没有收到新的日线数据')
    } else {
      ElMessage.warning(result.message)
    }
    if (instrument.id === selectedInstrumentId.value) {
      await loadPrices()
    }
  } finally {
    syncing.value = false
    syncingInstrumentId.value = null
  }
}

function goToOrder() {
  if (!selectedInstrumentId.value || selectedInstrument.value?.assetType === 'INDEX') return
  void router.push({ path: '/orders', query: { instrumentId: String(selectedInstrumentId.value) } })
}

function selectInstrument(id: number) {
  selectedInstrumentId.value = id
  prices.value = []
  summary.value = null
  void loadPrices()
}

function reconcileSelectedInstrument() {
  if (
    selectedInstrumentId.value &&
    instruments.value.some((instrument) => instrument.id === selectedInstrumentId.value)
  ) {
    return
  }
  selectedInstrumentId.value = filteredInstruments.value[0]?.id ?? null
}

async function handleInstrumentCommand(instrument: SecurityInstrument, command: unknown) {
  if (command === 'toggle-status') {
    await toggleInstrumentStatus(instrument)
    return
  }
  if (command === 'clear-prices') {
    await clearDailyPrices(instrument)
    return
  }
  if (command === 'delete') {
    await removeInstrument(instrument)
    return
  }
  if (command === 'force-delete') {
    await forceRemoveInstrument(instrument)
  }
}

async function toggleInstrumentStatus(instrument: SecurityInstrument) {
  const nextStatus = instrument.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionText = nextStatus === 'DISABLED' ? '停用' : '恢复'
  await ElMessageBox.confirm(
    `${actionText}“${instrumentLabel(instrument)}”？\n\n停用后仍可查看历史 K 线和已有交易映射，但不能创建新订单，也不会参与行情同步。`,
    `${actionText}证券标的`,
    { type: nextStatus === 'DISABLED' ? 'warning' : 'info', confirmButtonText: actionText },
  )
  await updateInstrumentStatus(instrument.id, nextStatus)
  ElMessage.success(`${actionText}成功`)
  await loadInstruments()
}

async function clearDailyPrices(instrument: SecurityInstrument) {
  await ElMessageBox.confirm(
    `清空“${instrumentLabel(instrument)}”的全部历史日线数据？\n\n该操作会删除本地 K 线和成交量数据，但不会删除证券标的、持仓、订单、成交和资金流水。清空后可以重新同步。`,
    '清空历史行情',
    { type: 'warning', confirmButtonText: '清空' },
  )
  await clearInstrumentDailyPrices(instrument.id)
  ElMessage.success('历史日线已清空，可重新同步行情')
  if (instrument.id === selectedInstrumentId.value) {
    prices.value = []
    summary.value = null
    await loadPrices()
  }
}

async function removeInstrument(instrument: SecurityInstrument) {
  await ElMessageBox.confirm(
    `永久删除完全未使用的证券标的“${instrumentLabel(instrument)}”？\n\n已有持仓、订单、成交或资金流水时，服务端会拒绝删除。该操作不可恢复。`,
    '永久删除证券标的',
    { type: 'error', confirmButtonText: '永久删除' },
  )
  const deletedSelected = instrument.id === selectedInstrumentId.value
  await deleteInstrument(instrument.id)
  ElMessage.success('证券标的已删除')
  if (deletedSelected) {
    selectedInstrumentId.value = null
    prices.value = []
    summary.value = null
  }
  await loadInstruments()
}

async function forceRemoveInstrument(instrument: SecurityInstrument) {
  await ElMessageBox.confirm(
    `硬删除证券标的“${instrumentLabel(instrument)}”？\n\n该操作会删除该证券相关的持仓、模拟订单、成交、资金流水、历史 K 线和同步日志。订单页将不再显示该证券。此操作不可恢复。`,
    '硬删除证券标的',
    { type: 'error', confirmButtonText: '硬删除' },
  )
  const deletedSelected = instrument.id === selectedInstrumentId.value
  await forceDeleteInstrument(instrument.id)
  ElMessage.success('证券标的及相关模拟数据已硬删除')
  if (deletedSelected) {
    selectedInstrumentId.value = null
    prices.value = []
    summary.value = null
  }
  await loadInstruments()
}

function rangeParams(key: RangeKey) {
  const now = new Date()
  const from = new Date(now)
  if (key === 'ALL') {
    from.setFullYear(from.getFullYear() - 30)
    return {
      from: toDateString(from),
      to: toDateString(now),
      limit: 10000,
    }
  }
  const monthMap: Record<Exclude<RangeKey, 'ALL'>, number> = {
    '1M': 1,
    '3M': 3,
    '6M': 6,
    '1Y': 12,
    '3Y': 36,
  }
  from.setMonth(from.getMonth() - monthMap[key])
  return {
    from: toDateString(from),
    to: toDateString(now),
    limit: 10000,
  }
}

function toDateString(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function restoreSelectedInstrument() {
  const raw = window.localStorage.getItem(storageKey)
  const id = raw ? Number(raw) : NaN
  return Number.isInteger(id) && id > 0 ? id : null
}

function instrumentLabel(instrument: SecurityInstrument) {
  return `${instrument.instrumentName}（${instrument.symbol}）`
}

function instrumentDisplayName(instrument: SecurityInstrument) {
  const name = instrument.instrumentName.trim()
  const normalizedName = name.toUpperCase()
  const symbol = instrument.symbol.toUpperCase()
  if (!name || normalizedName === symbol || normalizedName.startsWith(`${symbol}.`)) {
    return instrument.symbol
  }
  return name
}

function instrumentSecondaryText(instrument: SecurityInstrument) {
  const displayName = instrumentDisplayName(instrument)
  const assetType = assetTypeLabel(instrument.assetType)
  return displayName === instrument.symbol
    ? `${assetType} · ${instrument.exchange}`
    : `${instrument.symbol} · ${instrument.exchange}`
}

function catalogOptionLabel(item: InstrumentCatalogItem) {
  return `${item.instrumentName} · ${item.canonicalCode} · ${assetTypeLabel(item.assetType)}`
}

function assetTypeLabel(assetType: AssetType) {
  const labels: Record<AssetType, string> = {
    STOCK: '股票',
    ETF: 'ETF',
    INDEX: '指数',
  }
  return labels[assetType]
}

function exchangeLabel(exchange: Exchange) {
  const labels: Partial<Record<Exchange, string>> = {
    SSE: '上交所',
    SZSE: '深交所',
  }
  return labels[exchange] ?? exchange
}

function statusLabel(status: SecurityInstrument['status']) {
  const labels: Record<SecurityInstrument['status'], string> = {
    ACTIVE: '启用',
    DISABLED: '停用',
  }
  return labels[status]
}

function formatDecimal(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '暂无数据'
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue.toLocaleString('zh-CN') : String(value)
}

function signed(value: string | number | null | undefined, suffix = '') {
  if (value === null || value === undefined || value === '') return '暂无数据'
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) return `${value}${suffix}`
  return `${numberValue > 0 ? '+' : ''}${numberValue.toLocaleString('zh-CN')}${suffix}`
}

function changeClass(value: string | number | null | undefined) {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue) || numberValue === 0) return ''
  return numberValue > 0 ? 'change-positive' : 'change-negative'
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return '暂无数据'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<style scoped>
.market-layout {
  display: grid;
  grid-template-columns: minmax(220px, 248px) minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
}

.instrument-panel,
.market-main,
.chart-panel {
  min-width: 0;
}

.instrument-panel {
  display: flex;
  flex-direction: column;
  gap: 9px;
  min-height: 0;
  padding: 12px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
}

.panel-title-copy {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.panel-title small {
  color: var(--qg-text-muted);
  font-size: 12px;
  font-weight: 400;
}

.panel-icon-button,
.filter-button {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
}

.panel-icon-button {
  color: var(--qg-text);
  background: var(--qg-subtle);
  border-color: var(--qg-border);
}

.instrument-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 7px;
  align-items: center;
}

.filter-button.active {
  color: var(--qg-text);
  background: var(--qg-nav-active);
  border-color: var(--qg-text-muted);
}

.filter-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
}

.filter-row :deep(.el-select) {
  min-width: 0;
}

.instrument-empty {
  display: grid;
  gap: 4px;
  padding: 24px 12px;
  color: var(--qg-text-muted);
  text-align: center;
}

.instrument-empty strong {
  color: var(--qg-text);
  font-size: 14px;
}

.instrument-empty span {
  font-size: 12px;
}

.instrument-scroll {
  flex: 1;
  min-height: 480px;
}

.instrument-item {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px;
  align-items: center;
  border: 0;
  border-bottom: 1px solid var(--qg-border);
  background: transparent;
  padding: 4px;
  color: var(--qg-text);
  text-align: left;
  transition: background-color 0.16s ease;
}

.instrument-item.active {
  background: var(--qg-nav-active);
}

.instrument-item:hover {
  background: var(--qg-nav-hover);
}

.instrument-select {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 8px 6px;
  text-align: left;
}

.instrument-identity {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.instrument-identity strong,
.instrument-identity small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.instrument-identity strong {
  font-size: 14px;
  font-weight: 650;
}

.instrument-identity small {
  color: var(--qg-text-muted);
  font-size: 12px;
  font-weight: 400;
}

.instrument-status {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 5px;
  padding-top: 1px;
  color: var(--qg-text-muted);
  font-size: 11px;
  font-weight: 500;
}

.instrument-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--qg-text-muted);
}

.instrument-status.active i {
  background: #188038;
}

.instrument-status.disabled i {
  background: #8b8b8b;
}

.instrument-actions {
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: flex-end;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.instrument-item:hover .instrument-actions,
.instrument-item.active .instrument-actions,
.instrument-actions:focus-within {
  opacity: 1;
}

.instrument-action-button {
  width: 28px;
  height: 28px;
  color: var(--qg-text-muted);
}

.danger-menu-item {
  color: var(--el-color-danger);
}

.market-main {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  overflow: hidden;
  padding: 0;
}

.quote-header {
  border-bottom: 1px solid var(--qg-border);
}

.quote-primary {
  display: grid;
  grid-template-columns: minmax(180px, 1.5fr) minmax(120px, 0.7fr) minmax(120px, 0.7fr) auto;
  gap: 20px;
  align-items: center;
  padding: 16px 18px 12px;
}

.quote-primary > div,
.quote-meta > div {
  min-width: 0;
}

.quote-primary span,
.quote-meta span {
  display: block;
  color: var(--qg-text-muted);
  font-size: 11px;
}

.quote-primary strong,
.quote-meta strong {
  display: block;
  overflow: hidden;
  margin-top: 4px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quote-identity strong {
  font-size: 20px;
}

.quote-identity small,
.quote-sync-meta small {
  display: block;
  overflow: hidden;
  margin-top: 3px;
  color: var(--qg-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quote-price strong {
  font-size: 28px;
  line-height: 1;
}

.quote-change strong {
  font-size: 20px;
  line-height: 1.1;
}

.quote-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.change-positive {
  color: #d93025;
}

.change-negative {
  color: #188038;
}

.quote-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(96px, 0.7fr)) minmax(180px, 1.3fr);
  gap: 18px;
  align-items: center;
  padding: 10px 18px 12px;
  background: var(--qg-subtle);
}

.quote-meta strong {
  font-size: 14px;
}

.chart-toolbar {
  display: flex;
  min-width: 0;
  align-items: center;
  padding: 10px 14px 4px;
  background: var(--qg-panel);
}

.range-tools {
  display: flex;
  align-items: center;
  min-width: 0;
}

.range-tools :deep(.el-radio-button__inner) {
  min-width: 46px;
}

.chart-panel {
  display: grid;
  min-height: 0;
  padding: 4px 10px 10px;
  background: var(--qg-panel);
}

.chart-panel :deep(.chart) {
  height: min(560px, calc(100vh - 285px));
  min-height: 420px;
}

.catalog-select {
  width: 100%;
}

.catalog-option {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.catalog-option > span {
  display: grid;
  min-width: 0;
  line-height: 1.3;
}

.catalog-option small {
  color: var(--qg-text-muted);
}

.dialog-actions {
  flex-wrap: wrap;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin: 16px 0;
}

.dialog-table {
  margin-top: 12px;
}

@media (max-width: 1180px) {
  .market-layout {
    grid-template-columns: 1fr;
  }

  .instrument-scroll {
    flex: none;
    height: 260px;
    min-height: 0;
  }

  .instrument-item {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .quote-primary {
    grid-template-columns: minmax(180px, 1.4fr) repeat(2, minmax(110px, 0.7fr)) auto;
  }
}

@media (max-width: 760px) {
  .quote-primary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }

  .quote-actions {
    justify-content: flex-start;
  }

  .quote-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-toolbar {
    overflow-x: auto;
  }

  .chart-panel :deep(.chart) {
    height: 460px;
    min-height: 360px;
  }
}
</style>
