<template>
  <section class="page">
    <PageHeader title="总览" description="基于后端现有列表接口展示少量前端汇总。">
      <template #actions>
        <el-button :loading="loading" @click="loadDashboard">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <div class="summary-grid">
      <div v-for="item in items" :key="item.label" class="surface metric">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small v-if="item.hint">{{ item.hint }}</small>
      </div>
    </div>

    <div class="surface action-panel">
      <div>
        <h2>下一步入口</h2>
        <p>继续完成模拟订单、成交记录、资金流水和风险管理页面。</p>
      </div>
      <el-space>
        <el-button @click="$router.push('/orders')">模拟订单</el-button>
        <el-button @click="$router.push('/risk')">风险管理</el-button>
      </el-space>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { listAccounts } from '@/api/account'
import { listPortfolios } from '@/api/portfolio'
import { listPositionsByPortfolio } from '@/api/position'
import { usePortfolioStore } from '@/stores/portfolio'
import type { SimulationAccount } from '@/types/account'
import type { InvestmentPortfolio } from '@/types/portfolio'

const portfolioStore = usePortfolioStore()

const accounts = ref<SimulationAccount[]>([])
const portfolios = ref<InvestmentPortfolio[]>([])
const positionCount = ref<number | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const totalAvailableCash = computed(() =>
  accounts.value.reduce((sum, account) => sum + Number(account.availableCash || 0), 0),
)

const items = computed(() => [
  { label: '模拟账户', value: String(accounts.value.length) },
  { label: '投资组合', value: String(portfolios.value.length) },
  {
    label: '当前持仓',
    value: positionCount.value === null ? '请选择组合' : String(positionCount.value),
    hint: '按当前选中组合统计',
  },
  {
    label: '可用现金',
    value: formatMoney(totalAvailableCash.value),
    hint: '账户列表字段汇总',
  },
])

onMounted(() => {
  void loadDashboard()
})

async function loadDashboard() {
  loading.value = true
  errorMessage.value = ''

  try {
    const [accountRows, portfolioRows] = await Promise.all([listAccounts(), listPortfolios()])
    accounts.value = accountRows
    portfolios.value = portfolioRows

    const selectedPortfolioExists = portfolioRows.some(
      (portfolio) => portfolio.id === portfolioStore.selectedPortfolioId,
    )

    if (!selectedPortfolioExists) {
      portfolioStore.selectPortfolio(null)
    }

    if (portfolioStore.selectedPortfolioId) {
      const positions = await listPositionsByPortfolio(portfolioStore.selectedPortfolioId)
      positionCount.value = positions.length
    } else {
      positionCount.value = null
    }
  } catch {
    errorMessage.value = '总览数据加载失败，请确认后端服务是否已启动。'
  } finally {
    loading.value = false
  }
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}
</script>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric {
  display: grid;
  gap: 8px;
  padding: 18px;
}

.metric span {
  color: var(--qg-text-muted);
  font-size: 13px;
}

.metric strong {
  font-size: 24px;
}

.metric small {
  color: var(--qg-text-muted);
}

.action-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px;
}

h2 {
  margin: 0;
  font-size: 18px;
}

p {
  margin: 6px 0 0;
  color: var(--qg-text-muted);
}
</style>
