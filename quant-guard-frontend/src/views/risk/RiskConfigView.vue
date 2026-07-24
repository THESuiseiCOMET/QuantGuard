<template>
  <section class="page">
    <PageHeader title="风险管理" description="配置当前投资组合的买卖权限和单笔订单限制。">
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
      description="风险配置必须绑定到一个投资组合。请先到投资组合页面选择当前组合。"
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
        <span>组合 ID</span>
        <strong>{{ selectedPortfolioId ?? '未选择' }}</strong>
      </div>
      <div class="context-item">
        <span>风险配置状态</span>
        <el-tag :type="riskConfig ? 'success' : 'info'" effect="plain">
          {{ riskConfig ? statusLabel(riskConfig.status) : hasNoConfig ? '未创建' : '未加载' }}
        </el-tag>
      </div>
    </div>

    <el-alert
      v-if="selectedPortfolioId && hasNoConfig && !errorMessage"
      title="当前组合尚未创建风险配置"
      description="创建后，模拟订单提交会使用 allowBuy、allowSell 和最大单笔订单金额进行基础风控。"
      type="warning"
      show-icon
      :closable="false"
    />

    <div v-if="selectedPortfolioId" v-loading="loading" class="surface form-panel">
      <div class="form-heading">
        <div>
          <h2>{{ riskConfig ? '编辑风险配置' : '创建风险配置' }}</h2>
          <p>保存成功后会使用后端返回的数据刷新页面状态。</p>
        </div>
        <el-tag v-if="riskConfig" effect="plain">配置 ID：{{ riskConfig.id }}</el-tag>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="risk-form">
        <div class="switch-grid">
          <el-form-item label="允许买入" prop="allowBuy">
            <el-switch v-model="form.allowBuy" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="允许卖出" prop="allowSell">
            <el-switch v-model="form.allowSell" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </div>

        <el-form-item label="最大单笔订单金额" prop="maxSingleOrderAmount">
          <el-input-number
            v-model="form.maxSingleOrderAmount"
            :min="0.01"
            :precision="4"
            :step="1000"
            class="number-input"
            controls-position="right"
          />
          <span class="field-hint">单位：人民币元，不进行汇率换算。</span>
        </el-form-item>

        <el-form-item label="最大仓位比例（0–1）" prop="maxPositionRatio">
          <el-input-number
            v-model="form.maxPositionRatio"
            :min="0"
            :max="1"
            :precision="6"
            :step="0.05"
            class="number-input"
            controls-position="right"
          />
          <span class="field-hint">
            该字段当前仅保存配置，现阶段后端尚未执行最大仓位比例检查。
          </span>
        </el-form-item>

        <div v-if="riskConfig" class="meta-grid">
          <div>
            <span>创建时间</span>
            <strong>{{ formatDateTime(riskConfig.createdAt) }}</strong>
          </div>
          <div>
            <span>更新时间</span>
            <strong>{{ formatDateTime(riskConfig.updatedAt) }}</strong>
          </div>
        </div>

        <div class="form-actions">
          <el-button :disabled="submitting" @click="resetForm">重置</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">
            {{ riskConfig ? '保存修改' : '创建配置' }}
          </el-button>
        </div>
      </el-form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { AxiosError } from 'axios'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { listAccounts } from '@/api/account'
import { listPortfolios } from '@/api/portfolio'
import { createRiskConfig, getRiskConfigByPortfolioId, updateRiskConfig } from '@/api/risk'
import { usePortfolioStore } from '@/stores/portfolio'
import type { AccountStatus, SimulationAccount } from '@/types/account'
import type { InvestmentPortfolio } from '@/types/portfolio'
import type { PortfolioRiskConfig } from '@/types/risk'

interface RiskConfigForm {
  allowBuy: boolean
  allowSell: boolean
  maxSingleOrderAmount: number
  maxPositionRatio: number
}

const router = useRouter()
const portfolioStore = usePortfolioStore()

const accounts = ref<SimulationAccount[]>([])
const portfolios = ref<InvestmentPortfolio[]>([])
const riskConfig = ref<PortfolioRiskConfig | null>(null)
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const hasNoConfig = ref(false)
const formRef = ref<FormInstance>()
let requestSeq = 0

const form = reactive<RiskConfigForm>({
  allowBuy: true,
  allowSell: true,
  maxSingleOrderAmount: 50000,
  maxPositionRatio: 0.5,
})

const rules: FormRules<RiskConfigForm> = {
  allowBuy: [{ required: true, message: '请选择是否允许买入', trigger: 'change' }],
  allowSell: [{ required: true, message: '请选择是否允许卖出', trigger: 'change' }],
  maxSingleOrderAmount: [
    { required: true, message: '请输入最大单笔订单金额', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '金额必须大于等于 0.01', trigger: 'blur' },
    { validator: validateFourDecimalPlaces, trigger: 'blur' },
  ],
  maxPositionRatio: [
    { required: true, message: '请输入最大仓位比例', trigger: 'blur' },
    { type: 'number', min: 0, max: 1, message: '比例必须在 0 到 1 之间', trigger: 'blur' },
    { validator: validateSixDecimalPlaces, trigger: 'blur' },
  ],
}

const selectedPortfolioId = computed(() => portfolioStore.selectedPortfolioId)
const currentPortfolio = computed(() =>
  portfolios.value.find((portfolio) => portfolio.id === selectedPortfolioId.value),
)
const currentAccount = computed(() =>
  accounts.value.find((account) => account.id === currentPortfolio.value?.accountId),
)
const currentPortfolioName = computed(() => currentPortfolio.value?.portfolioName ?? '未选择')
const currentAccountName = computed(() => currentAccount.value?.accountName ?? '未选择')

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
  const portfolioId = selectedPortfolioId.value
  const seq = ++requestSeq
  errorMessage.value = ''

  if (!portfolioId) {
    riskConfig.value = null
    hasNoConfig.value = false
    return
  }

  loading.value = true

  try {
    const [accountRows, portfolioRows] = await Promise.all([listAccounts(), listPortfolios()])
    if (seq !== requestSeq) return
    accounts.value = accountRows
    portfolios.value = portfolioRows

    if (!portfolioRows.some((portfolio) => portfolio.id === portfolioId)) {
      portfolioStore.selectPortfolio(null)
      riskConfig.value = null
      hasNoConfig.value = false
      errorMessage.value = '当前选择的投资组合不存在或已不可用。'
      return
    }

    await loadRiskConfig(portfolioId, seq)
  } catch (error) {
    if (seq !== requestSeq) return
    riskConfig.value = null
    hasNoConfig.value = false
    errorMessage.value = errorToMessage(error, '风险配置页面加载失败，请确认后端服务是否已启动。')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

async function loadRiskConfig(portfolioId: number, seq: number) {
  try {
    const config = await getRiskConfigByPortfolioId(portfolioId)
    if (seq !== requestSeq) return
    riskConfig.value = config
    hasNoConfig.value = false
    fillForm(config)
  } catch (error) {
    if (seq !== requestSeq) return
    if (isMissingRiskConfig(error)) {
      riskConfig.value = null
      hasNoConfig.value = true
      resetFormToDefaults()
      return
    }
    riskConfig.value = null
    hasNoConfig.value = false
    errorMessage.value = errorToMessage(error, '风险配置加载失败，请稍后重试。')
  }
}

async function submitForm() {
  if (!formRef.value || !selectedPortfolioId.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  errorMessage.value = ''

  const payload = {
    maxSingleOrderAmount: form.maxSingleOrderAmount.toFixed(4),
    maxPositionRatio: form.maxPositionRatio.toFixed(6),
    allowBuy: form.allowBuy,
    allowSell: form.allowSell,
  }

  try {
    const existingConfig = riskConfig.value
    const saved = existingConfig
      ? await updateRiskConfig(existingConfig.id, payload)
      : await createRiskConfig({
          portfolioId: selectedPortfolioId.value,
          ...payload,
        })
    riskConfig.value = saved
    hasNoConfig.value = false
    fillForm(saved)
    ElMessage.success(existingConfig ? '风险配置已保存' : '风险配置已创建')
  } catch (error) {
    errorMessage.value = errorToMessage(error, '风险配置保存失败，请检查输入后重试。')
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  if (riskConfig.value) {
    fillForm(riskConfig.value)
  } else {
    resetFormToDefaults()
  }
  formRef.value?.clearValidate()
}

function fillForm(config: PortfolioRiskConfig) {
  form.allowBuy = config.allowBuy
  form.allowSell = config.allowSell
  form.maxSingleOrderAmount = Number(config.maxSingleOrderAmount)
  form.maxPositionRatio = Number(config.maxPositionRatio)
}

function resetFormToDefaults() {
  form.allowBuy = true
  form.allowSell = true
  form.maxSingleOrderAmount = 50000
  form.maxPositionRatio = 0.5
  formRef.value?.clearValidate()
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

function validateFourDecimalPlaces(_: unknown, value: number, callback: (error?: Error) => void) {
  if (!Number.isFinite(value)) {
    callback(new Error('金额必须是有效数字'))
    return
  }
  if (!/^\d{1,15}(\.\d{1,4})?$/.test(String(value))) {
    callback(new Error('金额最多 15 位整数和 4 位小数'))
    return
  }
  callback()
}

function validateSixDecimalPlaces(_: unknown, value: number, callback: (error?: Error) => void) {
  if (!Number.isFinite(value)) {
    callback(new Error('比例必须是有效数字'))
    return
  }
  if (!/^(0|1)(\.\d{1,6})?$/.test(String(value))) {
    callback(new Error('比例范围为 0 到 1，最多 6 位小数'))
    return
  }
  callback()
}

function statusLabel(status: AccountStatus) {
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
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
.form-heading p,
.meta-grid span {
  color: var(--qg-text-muted);
  font-size: 13px;
}

.context-item strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-panel {
  padding: 20px;
}

.form-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.form-heading h2 {
  margin: 0;
  font-size: 18px;
}

.form-heading p {
  margin: 6px 0 0;
}

.risk-form {
  max-width: 760px;
}

.switch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.number-input {
  width: 260px;
}

.field-hint {
  display: block;
  margin-top: 8px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 4px;
  padding: 14px;
  border: 1px solid var(--qg-border);
  border-radius: 8px;
  background: #f8fafc;
}

.meta-grid div {
  display: grid;
  gap: 4px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}

@media (max-width: 900px) {
  .context-panel,
  .switch-grid,
  .meta-grid {
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
