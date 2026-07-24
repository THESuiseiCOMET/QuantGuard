<template>
  <section class="page">
    <PageHeader title="模拟账户" description="连接后端真实账户接口，创建和查看本地模拟账户。">
      <template #actions>
        <el-space>
          <el-button :loading="loading" @click="loadAccounts">刷新</el-button>
          <el-button type="primary" @click="openCreateDialog">创建账户</el-button>
        </el-space>
      </template>
    </PageHeader>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <div class="surface section-body">
      <el-table v-loading="loading" :data="accounts" row-key="id" empty-text="暂无模拟账户">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="accountName" label="账户名称" min-width="160" />
        <el-table-column label="账户类型" width="130">
          <template #default="{ row }: { row: SimulationAccount }">
            <el-tag effect="plain">{{ accountTypeLabel(row.accountType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="初始资金" width="150" align="right">
          <template #default="{ row }: { row: SimulationAccount }">
            <MoneyText :value="row.initialCash" />
          </template>
        </el-table-column>
        <el-table-column label="可用现金" width="150" align="right">
          <template #default="{ row }: { row: SimulationAccount }">
            <MoneyText :value="row.availableCash" />
          </template>
        </el-table-column>
        <el-table-column label="冻结现金" width="150" align="right">
          <template #default="{ row }: { row: SimulationAccount }">
            <MoneyText :value="row.frozenCash" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }: { row: SimulationAccount }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }: { row: SimulationAccount }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }: { row: SimulationAccount }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" title="创建模拟账户" width="460px">
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
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import { createAccount, listAccounts } from '@/api/account'
import type {
  AccountStatus,
  AccountType,
  CreateSimulationAccountRequest,
  SimulationAccount,
} from '@/types/account'

const accounts = ref<SimulationAccount[]>([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const errorMessage = ref('')
const formRef = ref<FormInstance>()

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
    accounts.value = await listAccounts()
  } catch {
    errorMessage.value = '账户加载失败，请确认后端服务是否已启动。'
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true

  try {
    await createAccount({
      accountName: form.accountName.trim(),
      accountType: form.accountType,
      initialCash: form.initialCash.trim(),
    })
    ElMessage.success('账户创建成功')
    cancelCreate()
    await loadAccounts()
  } finally {
    submitting.value = false
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

function formatDateTime(value: string) {
  if (!value) return '暂无数据'

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

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
.section-body {
  padding: 16px;
}

.full-width {
  width: 100%;
}
</style>
