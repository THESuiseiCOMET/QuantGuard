<template>
  <section class="page">
    <PageHeader title="投资组合" description="按模拟账户筛选组合，并保存当前账户与组合选择。">
      <template #actions>
        <el-space>
          <el-button :loading="loading" @click="loadPageData">刷新</el-button>
          <el-button
            type="primary"
            :disabled="activeAccounts.length === 0"
            @click="openCreateDialog"
          >
            创建组合
          </el-button>
        </el-space>
      </template>
    </PageHeader>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <div class="surface section-body">
      <div class="toolbar">
        <el-select
          v-model="selectedAccountId"
          clearable
          class="account-select"
          placeholder="按账户筛选"
          @change="handleAccountChange"
        >
          <el-option
            v-for="account in accounts"
            :key="account.id"
            :label="accountOptionLabel(account)"
            :value="account.id"
          />
        </el-select>
        <span class="hint">仅保存账户 ID 和组合 ID，不缓存资金数据。</span>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredPortfolios"
        row-key="id"
        empty-text="暂无投资组合"
        highlight-current-row
        max-height="calc(100vh - 300px)"
        table-layout="fixed"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column
          prop="portfolioName"
          label="组合名称"
          min-width="170"
          show-overflow-tooltip
        />
        <el-table-column label="所属账户" min-width="170" show-overflow-tooltip>
          <template #default="{ row }: { row: InvestmentPortfolio }">
            {{ accountName(row.accountId) }}
          </template>
        </el-table-column>
        <el-table-column label="组合类型" width="130">
          <template #default="{ row }: { row: InvestmentPortfolio }">
            <el-tag effect="plain">{{ accountTypeLabel(row.portfolioType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }: { row: InvestmentPortfolio }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前选择" width="110">
          <template #default="{ row }: { row: InvestmentPortfolio }">
            <el-tag v-if="row.id === portfolioStore.selectedPortfolioId" type="success"
              >当前</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }: { row: InvestmentPortfolio }">
            <div class="row-actions">
              <el-button
                link
                type="primary"
                :disabled="row.status !== 'ACTIVE'"
                @click="selectPortfolio(row)"
              >
                选择
              </el-button>
              <el-button
                v-if="row.status === 'ACTIVE'"
                link
                type="warning"
                :loading="operatingId === row.id"
                @click="disablePortfolio(row)"
              >
                停用
              </el-button>
              <el-button
                v-else
                link
                type="success"
                :loading="operatingId === row.id"
                @click="restorePortfolio(row)"
              >
                恢复
              </el-button>
              <el-button
                link
                type="danger"
                :loading="operatingId === row.id"
                @click="removePortfolio(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" title="创建投资组合" width="min(500px, calc(100vw - 32px))">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="所属账户" prop="accountId">
          <el-select v-model="form.accountId" class="full-width" placeholder="请选择账户">
            <el-option
              v-for="account in activeAccounts"
              :key="account.id"
              :label="account.accountName"
              :value="account.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="组合名称" prop="portfolioName">
          <el-input v-model="form.portfolioName" maxlength="64" placeholder="例如：长期核心组合" />
        </el-form-item>

        <el-form-item label="组合类型" prop="portfolioType">
          <el-select v-model="form.portfolioType" class="full-width">
            <el-option
              v-for="option in accountTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="说明" prop="description">
          <el-input
            v-model="form.description"
            maxlength="255"
            placeholder="可选，最多 255 个字符"
            type="textarea"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="cancelCreate">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { listAccounts } from '@/api/account'
import {
  createPortfolio,
  deletePortfolio,
  listPortfolios,
  updatePortfolioStatus,
} from '@/api/portfolio'
import { usePortfolioStore } from '@/stores/portfolio'
import type { AccountStatus, AccountType, SimulationAccount } from '@/types/account'
import type { CreateInvestmentPortfolioRequest, InvestmentPortfolio } from '@/types/portfolio'

const portfolioStore = usePortfolioStore()

const accounts = ref<SimulationAccount[]>([])
const portfolios = ref<InvestmentPortfolio[]>([])
const loading = ref(false)
const submitting = ref(false)
const operatingId = ref<number | null>(null)
const dialogVisible = ref(false)
const errorMessage = ref('')
const formRef = ref<FormInstance>()
const selectedAccountId = ref<number | null>(portfolioStore.selectedAccountId)

const form = reactive<CreateInvestmentPortfolioRequest>({
  accountId: portfolioStore.selectedAccountId ?? 0,
  portfolioName: '',
  portfolioType: 'LONG_TERM',
  description: '',
})

const accountTypeOptions: Array<{ label: string; value: AccountType }> = [
  { label: '长期账户', value: 'LONG_TERM' },
  { label: '短期账户', value: 'SHORT_TERM' },
]

const rules: FormRules<CreateInvestmentPortfolioRequest> = {
  accountId: [
    { required: true, message: '请选择所属账户', trigger: 'change' },
    { type: 'number', min: 1, message: '请选择有效账户', trigger: 'change' },
  ],
  portfolioName: [
    { required: true, message: '请输入组合名称', trigger: 'blur' },
    { max: 64, message: '组合名称最多 64 个字符', trigger: 'blur' },
  ],
  portfolioType: [{ required: true, message: '请选择组合类型', trigger: 'change' }],
  description: [{ max: 255, message: '说明最多 255 个字符', trigger: 'blur' }],
}

const filteredPortfolios = computed(() => {
  if (!selectedAccountId.value) return portfolios.value
  return portfolios.value.filter((portfolio) => portfolio.accountId === selectedAccountId.value)
})

const activeAccounts = computed(() =>
  accounts.value.filter((account) => account.status === 'ACTIVE'),
)

onMounted(() => {
  void loadPageData()
})

async function loadPageData() {
  loading.value = true
  errorMessage.value = ''

  try {
    const [accountRows, portfolioRows] = await Promise.all([listAccounts(), listPortfolios()])
    accounts.value = accountRows
    portfolios.value = portfolioRows
    reconcileSelection()
  } catch {
    errorMessage.value = '投资组合加载失败，请确认后端服务是否已启动。'
  } finally {
    loading.value = false
  }
}

function handleAccountChange(accountId: number | null) {
  portfolioStore.selectAccount(accountId)
  if (
    accountId !== null &&
    portfolioStore.selectedPortfolioId !== null &&
    !filteredPortfolios.value.some(
      (portfolio) => portfolio.id === portfolioStore.selectedPortfolioId,
    )
  ) {
    portfolioStore.selectPortfolio(null)
  }
}

function selectPortfolio(row: InvestmentPortfolio) {
  if (row.status !== 'ACTIVE') return
  portfolioStore.selectPortfolio(row?.id ?? null)
  portfolioStore.selectAccount(row?.accountId ?? selectedAccountId.value)
  selectedAccountId.value = portfolioStore.selectedAccountId
}

function openCreateDialog() {
  resetForm()
  const selectedAccount = activeAccounts.value.find(
    (account) => account.id === selectedAccountId.value,
  )
  form.accountId = selectedAccount?.id ?? activeAccounts.value[0]?.id ?? 0
  dialogVisible.value = true
}

function cancelCreate() {
  dialogVisible.value = false
  resetForm()
}

async function disablePortfolio(portfolio: InvestmentPortfolio) {
  await ElMessageBox.confirm(
    `停用后不能再通过该组合创建订单，历史持仓、成交和流水仍会保留。\n\n确认停用“${portfolio.portfolioName}”？`,
    '停用投资组合',
    { confirmButtonText: '停用', cancelButtonText: '取消', type: 'warning' },
  )
  await operatePortfolio(portfolio.id, async () => {
    await updatePortfolioStatus(portfolio.id, 'DISABLED')
    portfolioStore.clearPortfolioIfSelected(portfolio.id)
    ElMessage.success('组合已停用')
  })
}

async function restorePortfolio(portfolio: InvestmentPortfolio) {
  await operatePortfolio(portfolio.id, async () => {
    await updatePortfolioStatus(portfolio.id, 'ACTIVE')
    ElMessage.success('组合已恢复')
  })
}

async function removePortfolio(portfolio: InvestmentPortfolio) {
  await ElMessageBox.confirm(
    `永久删除空投资组合“${portfolio.portfolioName}”？\n此操作不可恢复。存在持仓、风险配置、订单、成交或流水时，服务端将拒绝删除。`,
    '永久删除投资组合',
    { confirmButtonText: '永久删除', cancelButtonText: '取消', type: 'error' },
  )
  await operatePortfolio(portfolio.id, async () => {
    try {
      await deletePortfolio(portfolio.id)
      portfolioStore.clearPortfolioIfSelected(portfolio.id)
      ElMessage.success('组合已删除')
    } catch (error) {
      ElMessage.warning('删除失败，可改为停用。')
      throw error
    }
  })
}

async function operatePortfolio(portfolioId: number, operation: () => Promise<void>) {
  operatingId.value = portfolioId
  try {
    await operation()
    await loadPageData()
  } finally {
    operatingId.value = null
  }
}

async function submitCreate() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true

  try {
    const portfolio = await createPortfolio({
      accountId: form.accountId,
      portfolioName: form.portfolioName.trim(),
      portfolioType: form.portfolioType,
      description: form.description?.trim() || undefined,
    })
    ElMessage.success('投资组合创建成功')
    selectedAccountId.value = portfolio.accountId
    portfolioStore.selectAccount(portfolio.accountId)
    portfolioStore.selectPortfolio(portfolio.id)
    cancelCreate()
    await loadPageData()
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.accountId =
    activeAccounts.value.find((account) => account.id === selectedAccountId.value)?.id ?? 0
  form.portfolioName = ''
  form.portfolioType = 'LONG_TERM'
  form.description = ''
  formRef.value?.clearValidate()
}

function accountName(accountId: number) {
  const account = accounts.value.find((item) => item.id === accountId)
  return account ? accountOptionLabel(account) : `账户 #${accountId}`
}

function accountOptionLabel(account: SimulationAccount) {
  return account.status === 'ACTIVE' ? account.accountName : `${account.accountName}（已停用）`
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
  } else if (portfolioStore.selectedAccountId !== selectedPortfolio.accountId) {
    portfolioStore.selectAccount(selectedPortfolio.accountId)
  }

  selectedAccountId.value = portfolioStore.selectedAccountId
}

function accountTypeLabel(type: AccountType) {
  return accountTypeOptions.find((option) => option.value === type)?.label ?? type
}

function statusLabel(status: AccountStatus) {
  const labels: Record<AccountStatus, string> = {
    ACTIVE: '启用',
    DISABLED: '停用',
  }
  return labels[status]
}
</script>

<style scoped>
.section-body {
  display: grid;
  gap: 16px;
  min-width: 0;
  padding: 16px;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  flex-wrap: wrap;
}

.account-select {
  width: min(260px, 100%);
}

.hint {
  color: var(--qg-text-muted);
  font-size: 13px;
}

.full-width {
  width: 100%;
}

.row-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
