<template>
  <section class="page">
    <PageHeader
      title="持仓"
      description="根据当前投资组合查询真实持仓；实时行情和浮动盈亏尚未接入。"
    >
      <template #actions>
        <el-button
          :loading="loading"
          :disabled="!portfolioStore.selectedPortfolioId"
          @click="loadPositions"
        >
          刷新
        </el-button>
      </template>
    </PageHeader>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <el-alert
      v-if="!portfolioStore.selectedPortfolioId"
      title="请先在投资组合页面选择一个当前组合。"
      type="info"
      show-icon
      :closable="false"
    />

    <div class="surface section-body">
      <el-table
        v-loading="loading"
        :data="positions"
        row-key="id"
        empty-text="暂无持仓"
        max-height="calc(100vh - 240px)"
        table-layout="fixed"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="portfolioId" label="组合 ID" width="100" />
        <el-table-column prop="instrumentId" label="标的 ID" width="100" />
        <el-table-column label="标的代码" width="140">
          <template #default>接口尚未提供</template>
        </el-table-column>
        <el-table-column label="标的名称" width="160">
          <template #default>接口尚未提供</template>
        </el-table-column>
        <el-table-column label="总数量" min-width="140" align="right">
          <template #default="{ row }: { row: PortfolioPosition }">
            <MoneyText :value="row.totalQuantity" />
          </template>
        </el-table-column>
        <el-table-column label="可用数量" min-width="140" align="right">
          <template #default="{ row }: { row: PortfolioPosition }">
            <MoneyText :value="row.availableQuantity" />
          </template>
        </el-table-column>
        <el-table-column label="冻结数量" min-width="140" align="right">
          <template #default="{ row }: { row: PortfolioPosition }">
            <MoneyText :value="row.frozenQuantity" />
          </template>
        </el-table-column>
        <el-table-column label="平均成本" min-width="140" align="right">
          <template #default="{ row }: { row: PortfolioPosition }">
            <MoneyText :value="row.averageCost" />
          </template>
        </el-table-column>
        <el-table-column label="浮动盈亏" width="140">
          <template #default>实时行情尚未接入</template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import { listPositionsByPortfolio } from '@/api/position'
import { usePortfolioStore } from '@/stores/portfolio'
import type { PortfolioPosition } from '@/types/position'

const portfolioStore = usePortfolioStore()
const positions = ref<PortfolioPosition[]>([])
const loading = ref(false)
const errorMessage = ref('')

onMounted(() => {
  void loadPositions()
})

watch(
  () => portfolioStore.selectedPortfolioId,
  () => {
    void loadPositions()
  },
)

async function loadPositions() {
  if (!portfolioStore.selectedPortfolioId) {
    positions.value = []
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    positions.value = await listPositionsByPortfolio(portfolioStore.selectedPortfolioId)
  } catch {
    errorMessage.value = '持仓加载失败，请确认后端服务是否已启动。'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.section-body {
  min-width: 0;
  padding: 16px;
}
</style>
