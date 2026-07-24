<template>
  <el-container class="main-layout">
    <el-aside class="sidebar" width="228px">
      <div class="brand">
        <div class="brand-mark">QG</div>
        <div>
          <div class="brand-name">QuantGuard</div>
          <div class="brand-subtitle">模拟交易与风控</div>
        </div>
      </div>

      <el-menu :default-active="activePath" router class="nav-menu" background-color="transparent">
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <div class="page-title">{{ pageTitle }}</div>
          <div class="page-subtitle">本地模拟环境，不接入真实交易</div>
        </div>
        <el-tag type="success" effect="plain">Backend 8080</el-tag>
      </el-header>

      <el-main class="content">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  DataAnalysis,
  Document,
  Files,
  Lock,
  Money,
  Monitor,
  Tickets,
  TrendCharts,
  Wallet,
} from '@element-plus/icons-vue'

const route = useRoute()

const navItems = [
  { path: '/dashboard', label: '总览', icon: DataAnalysis },
  { path: '/accounts', label: '模拟账户', icon: Money },
  { path: '/portfolios', label: '投资组合', icon: Files },
  { path: '/positions', label: '持仓', icon: TrendCharts },
  { path: '/orders', label: '模拟订单', icon: Tickets },
  { path: '/trades', label: '成交记录', icon: Document },
  { path: '/cash-ledger', label: '资金流水', icon: Wallet },
  { path: '/risk', label: '风险管理', icon: Lock },
  { path: '/system', label: '系统信息', icon: Monitor },
]

const activePath = computed(() => route.path)
const pageTitle = computed(() =>
  typeof route.meta.title === 'string' ? route.meta.title : 'QuantGuard',
)
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
}

.sidebar {
  color: #fff;
  background: var(--qg-sidebar);
}

.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  height: 72px;
  padding: 0 18px;
}

.brand-mark {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 8px;
  background: var(--qg-sidebar-active);
  font-weight: 700;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
}

.brand-subtitle {
  margin-top: 2px;
  color: #9ca3af;
  font-size: 12px;
}

.nav-menu {
  border-right: 0;
}

.nav-menu :deep(.el-menu-item) {
  color: #cbd5e1;
}

.nav-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: rgb(31 138 112 / 32%);
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--qg-border);
  background: #fff;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
}

.page-subtitle {
  margin-top: 3px;
  color: var(--qg-text-muted);
  font-size: 12px;
}

.content {
  padding: 20px;
}
</style>
