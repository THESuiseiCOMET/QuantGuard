<template>
  <section class="page">
    <PageHeader title="行情中心" description="导入 A 股证券标的，同步未复权历史日线并查看日 K。">
      <template #actions>
        <el-space>
          <el-button :loading="loading" @click="loadInstruments">刷新列表</el-button>
          <el-button type="primary" @click="dialogVisible = true">批量导入</el-button>
        </el-space>
      </template>
    </PageHeader>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <div class="market-layout">
      <aside class="surface instrument-panel">
        <div class="panel-title">股票列表</div>
        <el-input v-model="filters.keyword" placeholder="搜索代码或名称" clearable />
        <el-space class="filter-row" fill>
          <el-select v-model="filters.exchange" placeholder="交易所" clearable>
            <el-option label="上交所 SSE" value="SSE" />
            <el-option label="深交所 SZSE" value="SZSE" />
          </el-select>
          <el-select v-model="filters.status" placeholder="状态" clearable>
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-space>

        <el-alert
          v-if="filteredInstruments.length === 0"
          title="暂无 A 股证券标的"
          description="请先通过批量导入添加股票代码。"
          type="info"
          show-icon
          :closable="false"
        />

        <el-scrollbar height="560px">
          <button
            v-for="instrument in filteredInstruments"
            :key="instrument.id"
            class="instrument-item"
            :class="{ active: instrument.id === selectedInstrumentId }"
            type="button"
            @click="selectInstrument(instrument.id)"
          >
            <span>
              <strong>{{ instrument.symbol }}</strong>
              <small>{{ instrument.instrumentName }}</small>
            </span>
            <el-tag size="small" effect="plain">{{ instrument.exchange }}</el-tag>
          </button>
        </el-scrollbar>

        <el-button
          class="sync-all"
          :disabled="selectedIds.length === 0"
          :loading="batchSyncing"
          @click="syncSelected"
        >
          同步选中证券
        </el-button>
      </aside>

      <main class="market-main">
        <div class="surface summary-grid">
          <div>
            <span>当前证券</span>
            <strong>{{ selectedInstrumentLabel }}</strong>
          </div>
          <div>
            <span>最新日线日期</span>
            <strong>{{ summary?.latestTradeDate ?? '暂无数据' }}</strong>
          </div>
          <div>
            <span>最新收盘价</span>
            <strong>{{ formatDecimal(summary?.latestClose) }}</strong>
          </div>
          <div>
            <span>最新涨跌幅</span>
            <strong>{{ signed(summary?.latestPercentageChange, '%') }}</strong>
          </div>
          <div>
            <span>当前范围最高价</span>
            <strong>{{ formatDecimal(summary?.periodHigh) }}</strong>
          </div>
          <div>
            <span>当前范围最低价</span>
            <strong>{{ formatDecimal(summary?.periodLow) }}</strong>
          </div>
          <div>
            <span>数据条数</span>
            <strong>{{ prices.length }}</strong>
          </div>
          <div>
            <span>最后同步时间</span>
            <strong>{{ formatDateTime(summary?.lastSyncAt) }}</strong>
          </div>
        </div>

        <div class="surface toolbar">
          <el-radio-group v-model="rangeKey" @change="loadPrices">
            <el-radio-button label="1M">1个月</el-radio-button>
            <el-radio-button label="3M">3个月</el-radio-button>
            <el-radio-button label="6M">6个月</el-radio-button>
            <el-radio-button label="1Y">1年</el-radio-button>
            <el-radio-button label="3Y">3年</el-radio-button>
            <el-radio-button label="ALL">全部</el-radio-button>
          </el-radio-group>

          <el-space>
            <el-button
              :disabled="!selectedInstrumentId"
              :loading="priceLoading"
              @click="loadPrices"
            >
              刷新行情
            </el-button>
            <el-button
              type="primary"
              :disabled="!selectedInstrumentId"
              :loading="syncing"
              @click="syncCurrent"
            >
              同步当前证券
            </el-button>
          </el-space>
        </div>

        <el-alert
          title="最新日线收盘价，不是实时成交价。当前为未复权历史行情。"
          type="info"
          show-icon
          :closable="false"
        />

        <div class="surface chart-panel">
          <el-empty v-if="!selectedInstrumentId" description="请选择一只证券查看 K 线" />
          <el-empty
            v-else-if="!priceLoading && prices.length === 0"
            description="当前证券暂无历史日线，请先同步行情。"
          />
          <CandlestickChart v-else v-loading="priceLoading" :prices="prices" />
        </div>
      </main>
    </div>

    <el-dialog
      v-model="dialogVisible"
      title="批量导入 A 股证券"
      width="min(960px, calc(100vw - 32px))"
    >
      <el-form label-position="top">
        <el-form-item label="股票代码">
          <el-input
            v-model="importText"
            type="textarea"
            :rows="6"
            placeholder="每行输入一个股票代码，例如：&#10;600519&#10;000001.SZ&#10;SSE:601318"
          />
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
        <el-button :loading="previewing" @click="previewImport">解析预览</el-button>
        <el-button
          type="primary"
          :disabled="validPreviewCount === 0"
          :loading="importing"
          @click="confirmImport"
        >
          确认导入
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
        <el-table-column prop="instrumentName" label="股票名称" min-width="160" />
        <el-table-column prop="exchange" label="交易所" width="100" />
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
        <el-table-column prop="instrumentName" label="股票名称" min-width="150" />
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
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { listInstruments } from '@/api/instrument'
import {
  getInstrumentDailyPriceSummary,
  importInstruments,
  listInstrumentDailyPrices,
  previewInstrumentImport,
  syncInstrumentDailyPrices,
  syncMultipleInstrumentDailyPrices,
} from '@/api/marketData'
import type { SecurityInstrument } from '@/types/instrument'
import type {
  DailyPrice,
  DailyPriceSummary,
  ImportPreviewItem,
  InstrumentImportResult,
} from '@/types/marketData'
import CandlestickChart from './components/CandlestickChart.vue'

type RangeKey = '1M' | '3M' | '6M' | '1Y' | '3Y' | 'ALL'

const storageKey = 'quantguard.market-data.selectedInstrumentId'

const instruments = ref<SecurityInstrument[]>([])
const selectedInstrumentId = ref<number | null>(restoreSelectedInstrument())
const prices = ref<DailyPrice[]>([])
const summary = ref<DailyPriceSummary | null>(null)
const loading = ref(false)
const priceLoading = ref(false)
const syncing = ref(false)
const batchSyncing = ref(false)
const previewing = ref(false)
const importing = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const importText = ref('')
const importStartDate = ref<string | null>(null)
const syncOnImport = ref(true)
const previewItems = ref<ImportPreviewItem[]>([])
const importResults = ref<InstrumentImportResult[]>([])
const rangeKey = ref<RangeKey>('1Y')
let requestSeq = 0

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
    if (instrument.assetType !== 'STOCK') return false
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
  return `${selectedInstrument.value.symbol} · ${selectedInstrument.value.instrumentName}`
})

const selectedIds = computed(() =>
  filteredInstruments.value.filter((item) => item.status === 'ACTIVE').map((item) => item.id),
)

const validPreviewCount = computed(() => previewItems.value.filter((item) => item.valid).length)

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
      ? '行情服务尚未配置。请在本地环境设置 TUSHARE_TOKEN。'
      : '历史日线加载失败，请确认后端服务是否已启动。'
  } finally {
    if (seq === requestSeq) priceLoading.value = false
  }
}

async function previewImport() {
  const codes = parseImportInput()
  if (codes.length === 0) {
    ElMessage.warning('请输入股票代码')
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
      ? '行情服务尚未配置。请在本地环境设置 TUSHARE_TOKEN。'
      : '导入预览失败，请检查后端服务。'
  } finally {
    previewing.value = false
  }
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
  if (!selectedInstrumentId.value) return
  syncing.value = true

  try {
    const result = await syncInstrumentDailyPrices(selectedInstrumentId.value, {
      startDate: null,
      endDate: null,
      fullRefresh: false,
    })
    if (result.status === 'SUCCESS') {
      ElMessage.success('同步完成')
    } else {
      ElMessage.warning(result.message)
    }
    await loadPrices()
  } finally {
    syncing.value = false
  }
}

async function syncSelected() {
  if (selectedIds.value.length === 0) return
  batchSyncing.value = true

  try {
    const result = await syncMultipleInstrumentDailyPrices({ instrumentIds: selectedIds.value })
    ElMessage.success(`同步完成：成功 ${result.successCount}，失败 ${result.failedCount}`)
    await loadPrices()
  } finally {
    batchSyncing.value = false
  }
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

function parseImportInput() {
  return importText.value
    .split(/[,，;；\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function rangeParams(key: RangeKey) {
  if (key === 'ALL') return { limit: 10000 }
  const now = new Date()
  const from = new Date(now)
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
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 16px;
}

.instrument-panel,
.market-main,
.chart-panel {
  min-width: 0;
}

.instrument-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
}

.filter-row {
  width: 100%;
}

.instrument-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 0;
  border-bottom: 1px solid var(--qg-border);
  background: transparent;
  padding: 12px 4px;
  color: var(--qg-text);
  cursor: pointer;
  text-align: left;
}

.instrument-item.active {
  color: var(--qg-sidebar-active);
  font-weight: 700;
}

.instrument-item span {
  min-width: 0;
}

.instrument-item small {
  display: block;
  overflow: hidden;
  color: var(--qg-text-muted);
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sync-all {
  width: 100%;
}

.market-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
}

.summary-grid div {
  min-width: 0;
  border-right: 1px solid var(--qg-border);
  border-bottom: 1px solid var(--qg-border);
  padding: 14px 16px;
}

.summary-grid span {
  display: block;
  color: var(--qg-text-muted);
  font-size: 12px;
}

.summary-grid strong {
  display: block;
  overflow: hidden;
  margin-top: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  flex-wrap: wrap;
  padding: 12px 16px;
}

.chart-panel {
  padding: 16px;
}

.dialog-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin: 16px 0;
}

.dialog-table {
  margin-top: 12px;
}

@media (max-width: 1100px) {
  .market-layout {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
