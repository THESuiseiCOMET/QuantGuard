<template>
  <section class="page">
    <PageHeader
      title="模拟订单"
      description="按当前投资组合读取真实订单，并创建会冻结资金或持仓的模拟限价单。"
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
      description="模拟订单必须绑定到一个当前投资组合。"
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
        <span>可用资金</span>
        <strong><MoneyText :value="currentAccount?.availableCash" /></strong>
      </div>
      <div class="context-item">
        <span>冻结资金</span>
        <strong><MoneyText :value="currentAccount?.frozenCash" /></strong>
      </div>
      <div class="context-item">
        <span>风险配置状态</span>
        <el-tag :type="riskConfig ? 'success' : 'info'" effect="plain">
          {{
            riskConfig
              ? accountStatusLabel(riskConfig.status)
              : hasNoRiskConfig
                ? '未创建'
                : '未加载'
          }}
        </el-tag>
      </div>
    </div>

    <el-alert
      v-if="selectionMismatch"
      title="当前账户和投资组合不匹配"
      description="已禁止提交订单，请回到投资组合页面重新选择。"
      type="warning"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button type="primary" @click="router.push('/portfolios')">重新选择</el-button>
      </template>
    </el-alert>

    <div v-if="lastExecutionResult" class="surface execution-result">
      <div class="panel-heading">
        <h2>最近模拟成交</h2>
        <el-button link @click="lastExecutionResult = null">关闭</el-button>
      </div>
      <div class="result-grid">
        <div>
          <span>订单号</span>
          <strong>{{ lastExecutionResult.order.orderNo }}</strong>
        </div>
        <div>
          <span>订单状态</span>
          <strong>{{ orderStatusLabel(lastExecutionResult.order.status) }}</strong>
        </div>
        <div>
          <span>成交编号</span>
          <strong>{{ lastExecutionResult.trade.tradeNo }}</strong>
        </div>
        <div>
          <span>成交方向</span>
          <strong>{{ sideLabel(lastExecutionResult.trade.side) }}</strong>
        </div>
        <div>
          <span>成交数量</span>
          <strong><MoneyText :value="lastExecutionResult.trade.quantity" /></strong>
        </div>
        <div>
          <span>执行价格</span>
          <strong><MoneyText :value="lastExecutionResult.trade.executionPrice" /></strong>
        </div>
        <div>
          <span>成交金额</span>
          <strong><MoneyText :value="lastExecutionResult.trade.tradeAmount" /></strong>
        </div>
        <div>
          <span>执行时间</span>
          <strong>{{ formatDateTime(lastExecutionResult.trade.executedAt) }}</strong>
        </div>
      </div>
      <p>这是本地模拟成交，不是真实交易；执行价格来自订单限价，不代表实时市场撮合价。</p>
    </div>

    <el-alert
      v-if="selectedPortfolioId && hasNoRiskConfig && !errorMessage"
      title="当前组合尚未配置风险规则"
      description="创建订单前需要先配置 allowBuy、allowSell 和最大单笔订单金额。"
      type="warning"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button type="primary" @click="router.push('/risk')">前往风险管理</el-button>
      </template>
    </el-alert>

    <div v-if="selectedPortfolioId" class="work-grid">
      <div class="surface order-form-panel">
        <div class="panel-heading">
          <h2>创建限价订单</h2>
          <el-tag effect="plain">LIMIT</el-tag>
        </div>

        <el-alert
          v-if="activeInstruments.length === 0 && !loading"
          title="当前没有可用证券标的"
          description="订单页面只读取 ACTIVE 标的，本轮不提供证券创建表单。"
          type="info"
          show-icon
          :closable="false"
        />

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="order-form">
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

          <div v-if="selectedInstrument" class="instrument-meta">
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
          </div>

          <div v-if="selectedInstrument" class="position-meta">
            <div class="position-heading">
              <span>当前标的持仓</span>
              <strong>{{ selectedPosition ? '真实持仓' : '暂无该标的持仓' }}</strong>
            </div>
            <div class="position-grid">
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

          <el-form-item label="买卖方向" prop="side">
            <el-radio-group v-model="form.side" class="side-group">
              <el-radio-button value="BUY">买入 BUY</el-radio-button>
              <el-radio-button value="SELL">卖出 SELL</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="订单类型">
            <el-input model-value="限价单 LIMIT" disabled />
          </el-form-item>

          <el-form-item label="委托数量" prop="requestedQuantity">
            <el-input
              v-model.trim="form.requestedQuantity"
              inputmode="decimal"
              placeholder="例如：100"
            />
            <span class="field-hint">
              必须大于 0，最多 6 位小数，并且是当前证券 lotSize 的整数倍。
            </span>
          </el-form-item>

          <el-form-item label="限价" prop="limitPrice">
            <el-input
              v-model.trim="form.limitPrice"
              inputmode="decimal"
              placeholder="例如：10.5000"
            />
            <span class="field-hint">必须大于 0，最多 4 位小数；不接入实时行情。</span>
          </el-form-item>

          <div class="estimate-box">
            <span>预估委托金额</span>
            <strong>{{ estimatedAmountText }}</strong>
            <small>仅为前端预估，最终以服务端校验结果为准。</small>
          </div>

          <el-alert
            v-if="submitBlockReason"
            :title="submitBlockReason"
            type="warning"
            show-icon
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
              提交限价单
            </el-button>
          </div>
        </el-form>
      </div>

      <div class="surface risk-panel">
        <div class="panel-heading">
          <h2>风险摘要</h2>
          <el-button link type="primary" @click="router.push('/risk')">管理风险配置</el-button>
        </div>

        <el-skeleton v-if="loading && !riskConfig" :rows="5" animated />
        <el-empty v-else-if="hasNoRiskConfig" description="当前组合尚未配置风险规则" />
        <div v-else-if="riskConfig" class="risk-list">
          <div>
            <span>允许买入</span>
            <el-tag :type="riskConfig.allowBuy ? 'success' : 'danger'" effect="plain">
              {{ riskConfig.allowBuy ? '允许' : '禁止' }}
            </el-tag>
          </div>
          <div>
            <span>允许卖出</span>
            <el-tag :type="riskConfig.allowSell ? 'success' : 'danger'" effect="plain">
              {{ riskConfig.allowSell ? '允许' : '禁止' }}
            </el-tag>
          </div>
          <div>
            <span>最大单笔订单金额</span>
            <strong><MoneyText :value="riskConfig.maxSingleOrderAmount" /></strong>
          </div>
          <div>
            <span>最大仓位比例</span>
            <strong>{{ riskConfig.maxPositionRatio }}</strong>
          </div>
          <p>最大仓位比例当前仅保存配置，后端暂未执行该规则。</p>
        </div>
        <el-empty v-else description="风险配置尚未加载" />
      </div>
    </div>

    <div v-if="selectedPortfolioId" class="surface section-body">
      <div class="table-heading">
        <h2>订单列表</h2>
        <span>按当前投资组合读取</span>
      </div>

      <el-table
        v-loading="loading"
        :data="orders"
        row-key="id"
        empty-text="暂无模拟订单"
        max-height="calc(100vh - 320px)"
        table-layout="fixed"
      >
        <el-table-column prop="orderNo" label="订单号" min-width="230" show-overflow-tooltip />
        <el-table-column label="证券代码" min-width="140">
          <template #default="{ row }: { row: SimulatedOrder }">
            {{ instrumentText(row.instrumentId, 'symbol') }}
          </template>
        </el-table-column>
        <el-table-column label="证券名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }: { row: SimulatedOrder }">
            {{ instrumentText(row.instrumentId, 'name') }}
          </template>
        </el-table-column>
        <el-table-column label="方向" width="110">
          <template #default="{ row }: { row: SimulatedOrder }">
            <el-tag :type="row.side === 'BUY' ? 'success' : 'warning'" effect="plain">
              {{ sideLabel(row.side) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }: { row: SimulatedOrder }">
            {{ orderTypeLabel(row.orderType) }}
          </template>
        </el-table-column>
        <el-table-column label="委托数量" min-width="130" align="right">
          <template #default="{ row }: { row: SimulatedOrder }">
            <MoneyText :value="row.requestedQuantity" />
          </template>
        </el-table-column>
        <el-table-column label="限价" min-width="120" align="right">
          <template #default="{ row }: { row: SimulatedOrder }">
            <MoneyText :value="row.limitPrice" />
          </template>
        </el-table-column>
        <el-table-column label="预估委托金额" min-width="150" align="right">
          <template #default="{ row }: { row: SimulatedOrder }">
            <MoneyText :value="orderAmount(row)" />
          </template>
        </el-table-column>
        <el-table-column label="冻结金额" min-width="130" align="right">
          <template #default="{ row }: { row: SimulatedOrder }">
            <MoneyText :value="row.frozenAmount" />
          </template>
        </el-table-column>
        <el-table-column label="冻结数量" min-width="130" align="right">
          <template #default="{ row }: { row: SimulatedOrder }">
            <MoneyText :value="row.frozenQuantity" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }: { row: SimulatedOrder }">
            <el-tooltip :content="row.status" placement="top">
              <el-tag :type="orderStatusTag(row.status)" effect="plain">
                {{ orderStatusLabel(row.status) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="拒绝原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }: { row: SimulatedOrder }">
            {{ row.rejectionReason || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }: { row: SimulatedOrder }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }: { row: SimulatedOrder }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
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
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules, TagProps } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import { listAccounts } from '@/api/account'
import { listInstruments } from '@/api/instrument'
import { cancelOrder, createOrder, executeOrder, listOrdersByPortfolioId } from '@/api/order'
import { listPortfolios } from '@/api/portfolio'
import { listPositionsByPortfolio } from '@/api/position'
import { getRiskConfigByPortfolioId } from '@/api/risk'
import { usePortfolioStore } from '@/stores/portfolio'
import type { AccountStatus, SimulationAccount } from '@/types/account'
import type { SecurityInstrument, AssetType } from '@/types/instrument'
import type {
  CreateSimulatedOrderRequest,
  OrderSide,
  OrderStatus,
  OrderType,
  SimulatedOrder,
} from '@/types/order'
import type { InvestmentPortfolio } from '@/types/portfolio'
import type { PortfolioPosition } from '@/types/position'
import type { PortfolioRiskConfig } from '@/types/risk'
import type { ExecuteSimulatedOrderResponse } from '@/types/trade'

interface OrderForm {
  instrumentId: number | null
  side: OrderSide
  requestedQuantity: string
  limitPrice: string
}

type InstrumentTextField = 'symbol' | 'name'
type OrderAction = 'execute' | 'cancel'

const router = useRouter()
const portfolioStore = usePortfolioStore()

const accounts = ref<SimulationAccount[]>([])
const portfolios = ref<InvestmentPortfolio[]>([])
const instruments = ref<SecurityInstrument[]>([])
const orders = ref<SimulatedOrder[]>([])
const positions = ref<PortfolioPosition[]>([])
const riskConfig = ref<PortfolioRiskConfig | null>(null)
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const hasNoRiskConfig = ref(false)
const formRef = ref<FormInstance>()
const activeActionOrderId = ref<number | null>(null)
const activeAction = ref<OrderAction | null>(null)
const lastExecutionResult = ref<ExecuteSimulatedOrderResponse | null>(null)
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
  instruments.value.filter((instrument) => instrument.status === 'ACTIVE'),
)
const selectedInstrument = computed(() =>
  activeInstruments.value.find((instrument) => instrument.id === form.instrumentId),
)
const selectedPosition = computed(() =>
  positions.value.find((position) => position.instrumentId === form.instrumentId),
)
const selectionMismatch = computed(() => {
  if (!selectedAccountId.value || !currentPortfolio.value) return false
  return currentPortfolio.value.accountId !== selectedAccountId.value
})
const currentPortfolioName = computed(() => currentPortfolio.value?.portfolioName ?? '未选择')
const currentAccountName = computed(() => currentAccount.value?.accountName ?? '未选择')
const estimatedAmount = computed(() => multiplyDecimal(form.requestedQuantity, form.limitPrice, 4))
const estimatedAmountText = computed(() => {
  if (!estimatedAmount.value) return '-'
  const currency = selectedInstrument.value?.currency
  return currency ? `${estimatedAmount.value} ${currency}` : estimatedAmount.value
})
const submitBlockReason = computed(() => {
  if (!selectedPortfolioId.value) return '请先选择投资组合'
  if (!currentPortfolio.value) return '当前选择的投资组合不存在或已不可用'
  if (!selectedAccountId.value || !currentAccount.value) return '当前账户不存在或已不可用'
  if (currentAccount.value.status !== 'ACTIVE') return '当前账户已停用，请重新选择启用账户'
  if (currentPortfolio.value.status !== 'ACTIVE') return '当前投资组合已停用，请重新选择启用组合'
  if (selectionMismatch.value) return '当前账户和投资组合不匹配'
  if (hasNoRiskConfig.value) return '当前组合尚未配置风险规则'
  if (!riskConfig.value) return '风险配置尚未加载'
  if (activeInstruments.value.length === 0) return '当前没有可用证券标的'
  if (form.side === 'BUY' && !riskConfig.value.allowBuy) return '当前风险配置禁止买入'
  if (form.side === 'SELL' && !riskConfig.value.allowSell) return '当前风险配置禁止卖出'
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
  },
)

async function loadPageData() {
  const portfolioId = selectedPortfolioId.value
  const seq = ++requestSeq
  loading.value = true
  errorMessage.value = ''
  orders.value = []
  positions.value = []
  riskConfig.value = null
  hasNoRiskConfig.value = false

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

    const activeSelected = activeInstruments.value.find(
      (instrument) => instrument.id === form.instrumentId,
    )
    if (!activeSelected) form.instrumentId = activeInstruments.value[0]?.id ?? null

    const reconciledPortfolioId = selectedPortfolioId.value
    if (!portfolioId || !reconciledPortfolioId) return
    if (reconciledPortfolioId !== portfolioId) return
    if (selectionMismatch.value) return

    await Promise.all([
      loadRiskConfig(reconciledPortfolioId, seq),
      loadOrders(reconciledPortfolioId, seq),
      loadPositions(reconciledPortfolioId, seq),
    ])
  } catch (error) {
    if (seq !== requestSeq) return
    errorMessage.value = errorToMessage(error, '模拟订单页面加载失败，请确认后端服务是否已启动。')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
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

async function loadRiskConfig(portfolioId: number, seq: number) {
  try {
    const config = await getRiskConfigByPortfolioId(portfolioId)
    if (seq !== requestSeq) return
    riskConfig.value = config
    hasNoRiskConfig.value = false
  } catch (error) {
    if (seq !== requestSeq) return
    if (isMissingRiskConfig(error)) {
      riskConfig.value = null
      hasNoRiskConfig.value = true
      return
    }
    riskConfig.value = null
    hasNoRiskConfig.value = false
    errorMessage.value = errorToMessage(error, '风险配置加载失败，请稍后重试。')
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
    `确认执行订单 ${order.orderNo}？系统将按该限价立即完成模拟成交，并更新资金、持仓、成交和资金流水。该操作完成后不能取消。${sideDescription}`,
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
      lastExecutionResult.value = result
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
    portfolioStore.selectPortfolio(null)
    return
  }

  if (portfolioStore.selectedAccountId !== selectedPortfolio.accountId) {
    portfolioStore.selectAccount(selectedPortfolio.accountId)
  }
}

function isMissingRiskConfig(error: unknown) {
  if (!(error instanceof AxiosError)) return false
  return (
    error.response?.status === 404 &&
    error.response.data?.message === 'Portfolio risk config not found'
  )
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
    callback(new Error('限价必须大于 0，最多 15 位整数和 4 位小数'))
    return
  }
  if (Number(value) <= 0) {
    callback(new Error('限价必须大于 0'))
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

function sideLabel(side: OrderSide) {
  const labels: Record<OrderSide, string> = {
    BUY: '买入',
    SELL: '卖出',
  }
  return labels[side]
}

function orderTypeLabel(type: OrderType) {
  const labels: Record<OrderType, string> = {
    LIMIT: '限价',
  }
  return labels[type]
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
  }
  return labels[type]
}

function accountStatusLabel(status: AccountStatus) {
  const labels: Record<AccountStatus, string> = {
    ACTIVE: '启用',
    DISABLED: '停用',
  }
  return labels[status]
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
.field-hint,
.estimate-box span,
.estimate-box small,
.instrument-meta span,
.position-meta span,
.risk-list span,
.execution-result span,
.execution-result p,
.risk-list p,
.table-heading span {
  color: var(--qg-text-muted);
  font-size: 13px;
}

.context-item strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
  gap: 16px;
}

.order-form-panel,
.risk-panel,
.section-body {
  min-width: 0;
  padding: 16px;
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

.order-form {
  display: grid;
  gap: 2px;
}

.full-width {
  width: 100%;
}

.instrument-meta,
.position-meta,
.execution-result {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
  background: #f8fafc;
}

.instrument-meta {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.instrument-meta div,
.risk-list div,
.position-grid div,
.result-grid div {
  display: grid;
  gap: 4px;
}

.position-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.position-grid,
.result-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.execution-result {
  background: #ffffff;
}

.execution-result p {
  margin: 0;
}

.side-group {
  width: 100%;
}

.field-hint {
  display: block;
  margin-top: 8px;
}

.estimate-box {
  display: grid;
  gap: 4px;
  margin: 4px 0 14px;
  padding: 12px;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
  background: #f8fafc;
}

.estimate-box strong {
  font-size: 20px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.risk-list {
  display: grid;
  gap: 14px;
}

.risk-list div {
  grid-template-columns: 1fr auto;
  align-items: center;
}

.risk-list p {
  margin: 0;
}

.muted {
  color: var(--qg-text-muted);
}

@media (max-width: 1100px) {
  .context-panel,
  .work-grid,
  .instrument-meta,
  .position-grid,
  .result-grid {
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
