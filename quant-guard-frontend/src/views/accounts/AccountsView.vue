<template>
  <section class="page">
    <PageHeader title="模拟账户">
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          创建账户
        </el-button>
      </template>
    </PageHeader>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />

    <div class="surface section-body">
      <div v-if="selectedAccounts.length > 0" class="bulk-toolbar">
        <span class="hint">已选 {{ selectedAccounts.length }} 个账户</span>
        <el-button size="small" :loading="bulkOperating" @click="batchDisableAccounts">
          批量停用
        </el-button>
        <el-button size="small" :loading="bulkOperating" @click="batchRestoreAccounts">
          批量恢复
        </el-button>
        <el-button
          size="small"
          type="danger"
          plain
          :loading="bulkOperating"
          @click="batchRemoveAccounts"
        >
          批量删除空账户
        </el-button>
        <el-button
          size="small"
          type="danger"
          :loading="bulkOperating"
          @click="batchForceRemoveAccounts"
        >
          批量硬删除
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="accounts"
        :current-row-key="portfolioStore.selectedAccountId"
        row-key="id"
        empty-text="暂无模拟账户"
        highlight-current-row
        max-height="calc(100vh - 160px)"
        table-layout="fixed"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="44" fixed="left" />
        <el-table-column label="账户" min-width="240">
          <template #default="{ row }: { row: SimulationAccount }">
            <div class="account-name">
              <div class="account-title">
                <strong>{{ row.accountName }}</strong>
                <el-tag v-if="isCurrentAccount(row)" size="small" type="success" effect="plain">
                  当前
                </el-tag>
              </div>
              <span>{{ accountTypeLabel(row.accountType) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="可用资金" min-width="150" align="right">
          <template #default="{ row }: { row: SimulationAccount }">
            <MoneyText :value="row.availableCash" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }: { row: SimulationAccount }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }: { row: SimulationAccount }">
            <div class="row-actions">
              <el-button
                size="small"
                type="primary"
                plain
                :disabled="row.status !== 'ACTIVE' || isCurrentAccount(row)"
                @click="selectAccount(row)"
              >
                {{ isCurrentAccount(row) ? '正在使用' : '使用账户' }}
              </el-button>
              <el-dropdown
                trigger="click"
                :disabled="operatingId === row.id"
                @command="handleAccountCommand(row, $event)"
              >
                <el-button link>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="toggle-status">
                      {{ row.status === 'ACTIVE' ? '停用账户' : '恢复账户' }}
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided class="danger-menu-item">
                      删除空账户
                    </el-dropdown-item>
                    <el-dropdown-item command="force-delete" class="danger-menu-item">
                      删除账户及模拟数据
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" title="创建模拟账户" width="min(460px, calc(100vw - 32px))">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="账户名称" prop="accountName">
          <el-input v-model="form.accountName" maxlength="64" placeholder="例如：长期模拟账户" />
        </el-form-item>

        <el-form-item label="账户类型" prop="accountType">
          <el-select v-model="form.accountType" class="full-width">
            <el-option
              v-for="option in accountTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="初始资金" prop="initialCash">
          <el-input v-model="form.initialCash" placeholder="例如：100000.00" />
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
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import {
  createAccount,
  deleteAccount,
  forceDeleteAccount,
  listAccounts,
  updateAccountStatus,
} from '@/api/account'
import { listPortfolios } from '@/api/portfolio'
import { usePortfolioStore } from '@/stores/portfolio'
import type {
  AccountStatus,
  AccountType,
  CreateSimulationAccountRequest,
  SimulationAccount,
} from '@/types/account'
import type { InvestmentPortfolio } from '@/types/portfolio'

const accounts = ref<SimulationAccount[]>([])
const portfolios = ref<InvestmentPortfolio[]>([])
const portfolioStore = usePortfolioStore()
const loading = ref(false)
const submitting = ref(false)
const bulkOperating = ref(false)
const operatingId = ref<number | null>(null)
const dialogVisible = ref(false)
const errorMessage = ref('')
const formRef = ref<FormInstance>()
const selectedAccounts = ref<SimulationAccount[]>([])

const form = reactive<CreateSimulationAccountRequest>({
  accountName: '',
  accountType: 'LONG_TERM',
  initialCash: '',
})

const accountTypeOptions: Array<{ label: string; value: AccountType }> = [
  { label: '长期账户', value: 'LONG_TERM' },
  { label: '短期账户', value: 'SHORT_TERM' },
]

const rules: FormRules<CreateSimulationAccountRequest> = {
  accountName: [
    { required: true, message: '请输入账户名称', trigger: 'blur' },
    { max: 64, message: '账户名称最多 64 个字符', trigger: 'blur' },
  ],
  accountType: [{ required: true, message: '请选择账户类型', trigger: 'change' }],
  initialCash: [
    { required: true, message: '请输入初始资金', trigger: 'blur' },
    {
      pattern: /^(0|[1-9]\d{0,14})(\.\d{1,2})?$/,
      message: '请输入非负金额，最多 2 位小数',
      trigger: 'blur',
    },
  ],
}

onMounted(() => {
  void loadAccounts()
})

async function loadAccounts() {
  loading.value = true
  errorMessage.value = ''

  try {
    const [accountRows, portfolioRows] = await Promise.all([listAccounts(), listPortfolios()])
    accounts.value = accountRows
    portfolios.value = portfolioRows
    selectedAccounts.value = selectedAccounts.value.filter((selected) =>
      accounts.value.some((account) => account.id === selected.id),
    )
    reconcileAccountSelection()
  } catch {
    errorMessage.value = '账户加载失败，请确认后端服务是否已启动。'
  } finally {
    loading.value = false
  }
}

function handleSelectionChange(rows: SimulationAccount[]) {
  selectedAccounts.value = rows
}

function selectAccount(account: SimulationAccount) {
  if (account.status !== 'ACTIVE') return

  const activePortfolios = portfolios.value.filter(
    (portfolio) => portfolio.accountId === account.id && portfolio.status === 'ACTIVE',
  )
  const currentPortfolio = activePortfolios.find(
    (portfolio) => portfolio.id === portfolioStore.selectedPortfolioId,
  )
  const portfolio = currentPortfolio ?? activePortfolios[0]

  portfolioStore.selectAccount(account.id)
  portfolioStore.selectPortfolio(portfolio?.id ?? null)
  if (!portfolio) {
    ElMessage.warning('该账户暂无可用交易环境')
  }
}

function reconcileAccountSelection() {
  const activeAccounts = accounts.value.filter((account) => account.status === 'ACTIVE')
  const selectedAccount = activeAccounts.find(
    (account) => account.id === portfolioStore.selectedAccountId,
  )
  const fallbackAccount = activeAccounts.find((account) =>
    portfolios.value.some(
      (portfolio) => portfolio.accountId === account.id && portfolio.status === 'ACTIVE',
    ),
  )
  const account = selectedAccount ?? fallbackAccount ?? activeAccounts[0]

  if (!account) {
    portfolioStore.selectAccount(null)
    return
  }

  const activePortfolios = portfolios.value.filter(
    (portfolio) => portfolio.accountId === account.id && portfolio.status === 'ACTIVE',
  )
  const selectedPortfolio = activePortfolios.find(
    (portfolio) => portfolio.id === portfolioStore.selectedPortfolioId,
  )
  const portfolio = selectedPortfolio ?? activePortfolios[0]
  portfolioStore.selectAccount(account.id)
  portfolioStore.selectPortfolio(portfolio?.id ?? null)
}

function isCurrentAccount(account: SimulationAccount) {
  return portfolioStore.selectedAccountId === account.id
}

function handleAccountCommand(account: SimulationAccount, command: string | number | object) {
  if (command === 'toggle-status') {
    if (account.status === 'ACTIVE') {
      void disableAccount(account)
    } else {
      void restoreAccount(account)
    }
  } else if (command === 'delete') {
    void removeAccount(account)
  } else if (command === 'force-delete') {
    void forceRemoveAccount(account)
  }
}

async function submitCreate() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true

  try {
    const account = await createAccount({
      accountName: form.accountName.trim(),
      accountType: form.accountType,
      initialCash: form.initialCash.trim(),
    })
    portfolioStore.selectAccount(account.id)
    portfolioStore.selectPortfolio(null)
    ElMessage.success('账户创建成功')
    cancelCreate()
    await loadAccounts()
  } finally {
    submitting.value = false
  }
}

async function disableAccount(account: SimulationAccount) {
  try {
    await ElMessageBox.confirm(
      `停用后不能再通过该账户创建订单，历史数据仍会保留。\n\n确认停用“${account.accountName}”？`,
      '停用模拟账户',
      { confirmButtonText: '停用', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await operateAccount(account.id, async () => {
    await updateAccountStatus(account.id, 'DISABLED')
    portfolioStore.clearAccountIfSelected(account.id)
    ElMessage.success('账户已停用')
  })
}

async function restoreAccount(account: SimulationAccount) {
  await operateAccount(account.id, async () => {
    await updateAccountStatus(account.id, 'ACTIVE')
    ElMessage.success('账户已恢复')
  })
}

async function removeAccount(account: SimulationAccount) {
  try {
    await ElMessageBox.confirm(
      `永久删除空账户“${account.accountName}”？\n此操作不可恢复。只有现金未变动、没有订单、成交、流水和持仓的空账户可以删除。已有交易数据的账户请改为停用。`,
      '永久删除模拟账户',
      { confirmButtonText: '永久删除', cancelButtonText: '取消', type: 'error' },
    )
  } catch {
    return
  }
  await operateAccount(account.id, async () => {
    await deleteAccount(account.id)
    portfolioStore.clearAccountIfSelected(account.id)
    ElMessage.success('账户已删除')
  })
}

async function forceRemoveAccount(account: SimulationAccount) {
  try {
    await ElMessageBox.confirm(
      `硬删除模拟账户“${account.accountName}”？\n\n该操作会删除该账户下的持仓、订单、成交和资金流水，仅保留证券标的与行情基础数据。此操作不可恢复。`,
      '硬删除模拟账户',
      { confirmButtonText: '硬删除', cancelButtonText: '取消', type: 'error' },
    )
  } catch {
    return
  }
  await operateAccount(account.id, async () => {
    await forceDeleteAccount(account.id)
    portfolioStore.clearAccountIfSelected(account.id)
    ElMessage.success('账户模拟数据已硬删除')
  })
}

async function batchDisableAccounts() {
  await runBatchAccounts('停用选中的账户？', '停用', async (account) => {
    if (account.status === 'ACTIVE') {
      await updateAccountStatus(account.id, 'DISABLED')
      portfolioStore.clearAccountIfSelected(account.id)
    }
  })
}

async function batchRestoreAccounts() {
  await runBatchAccounts('恢复选中的账户？', '恢复', async (account) => {
    if (account.status === 'DISABLED') {
      await updateAccountStatus(account.id, 'ACTIVE')
    }
  })
}

async function batchRemoveAccounts() {
  await runBatchAccounts(
    '批量删除选中的空账户？\n\n只有现金未变动、没有订单、成交、流水和持仓的空账户可以删除。',
    '删除空账户',
    async (account) => {
      await deleteAccount(account.id)
      portfolioStore.clearAccountIfSelected(account.id)
    },
    'error',
  )
}

async function batchForceRemoveAccounts() {
  await runBatchAccounts(
    '批量硬删除选中的模拟账户？\n\n这会删除账户下的持仓、订单、成交和资金流水，仅保留证券标的与行情基础数据。此操作不可恢复。',
    '硬删除',
    async (account) => {
      await forceDeleteAccount(account.id)
      portfolioStore.clearAccountIfSelected(account.id)
    },
    'error',
  )
}

async function runBatchAccounts(
  message: string,
  actionLabel: string,
  action: (account: SimulationAccount) => Promise<void>,
  type: 'warning' | 'error' = 'warning',
) {
  if (selectedAccounts.value.length === 0) return
  try {
    await ElMessageBox.confirm(message, `批量${actionLabel}`, {
      confirmButtonText: actionLabel,
      cancelButtonText: '取消',
      type,
    })
  } catch {
    return
  }

  bulkOperating.value = true
  let successCount = 0
  try {
    for (const account of selectedAccounts.value) {
      await action(account)
      successCount += 1
    }
    ElMessage.success(`已完成 ${successCount} 个账户`)
    await loadAccounts()
  } finally {
    bulkOperating.value = false
  }
}

async function operateAccount(accountId: number, operation: () => Promise<void>) {
  operatingId.value = accountId
  try {
    await operation()
    await loadAccounts()
  } finally {
    operatingId.value = null
  }
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function cancelCreate() {
  dialogVisible.value = false
  resetForm()
}

function resetForm() {
  form.accountName = ''
  form.accountType = 'LONG_TERM'
  form.initialCash = ''
  formRef.value?.clearValidate()
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
  gap: 10px;
  min-width: 0;
  padding: 12px;
}

.account-name span {
  color: var(--qg-text-muted);
  font-size: 12px;
}

.account-name {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.account-name strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-title {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.account-title strong {
  min-width: 0;
}

.bulk-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  flex-wrap: wrap;
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

.danger-menu-item {
  color: var(--el-color-danger);
}
</style>
