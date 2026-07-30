<template>
  <el-container class="main-layout" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
    <el-aside class="sidebar" width="var(--qg-sidebar-width)">
      <div class="brand">
        <div class="brand-name">QuantGuard</div>
        <el-tooltip :content="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'" placement="right">
          <el-button
            class="sidebar-action"
            text
            circle
            :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
            @click="sidebarCollapsed = !sidebarCollapsed"
          >
            <el-icon>
              <Expand v-if="sidebarCollapsed" />
              <Fold v-else />
            </el-icon>
          </el-button>
        </el-tooltip>
      </div>

      <el-menu :default-active="activePath" router class="nav-menu" background-color="transparent">
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span class="nav-label">{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <div class="theme-section">
        <el-tooltip
          :disabled="!sidebarCollapsed"
          :content="isDarkMode ? '切换浅色模式' : '切换深色模式'"
          placement="right"
        >
          <button
            class="theme-toggle"
            type="button"
            :disabled="themeTransitioning"
            :aria-label="isDarkMode ? '切换浅色模式' : '切换深色模式'"
            @click="toggleTheme"
          >
            <el-icon>
              <Moon v-if="isDarkMode" />
              <Sunny v-else />
            </el-icon>
            <span class="theme-label">{{ isDarkMode ? '深色模式' : '浅色模式' }}</span>
          </button>
        </el-tooltip>
      </div>
    </el-aside>

    <el-container class="main-pane">
      <el-main class="content">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>

  <Teleport to="body">
    <div
      v-if="themeOverlay"
      class="theme-transition-overlay"
      :class="{
        'is-expanded': themeOverlay.expanded,
        'is-fading': themeOverlay.fading,
      }"
      :style="themeOverlayStyle"
    ></div>
  </Teleport>
</template>

<script setup lang="ts">
/* global CustomEvent, Document, MouseEvent, document, localStorage, window */
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, Expand, Fold, Moon, Sunny, Tickets, User } from '@element-plus/icons-vue'
import { Calculator } from '@lucide/vue'

const route = useRoute()
const sidebarCollapsed = ref(false)
const savedTheme = localStorage.getItem('qg-theme')
const isDarkMode = ref(
  savedTheme === 'dark' ||
    (savedTheme === null && window.matchMedia('(prefers-color-scheme: dark)').matches),
)

type ThemeViewTransition = {
  ready: Promise<void>
}

type ThemeTransitionDocument = Document & {
  startViewTransition?: (callback: () => void) => ThemeViewTransition
}

type ThemeOverlay = {
  x: number
  y: number
  scale: number
  color: string
  expanded: boolean
  fading: boolean
}

const themeTransitioning = ref(false)
const themeOverlay = ref<ThemeOverlay | null>(null)
const themeOverlayStyle = computed(() => {
  if (!themeOverlay.value) return {}
  return {
    left: `${themeOverlay.value.x - 18}px`,
    top: `${themeOverlay.value.y - 18}px`,
    background: themeOverlay.value.color,
    '--theme-overlay-scale': String(themeOverlay.value.scale),
  }
})

const navItems = [
  { path: '/accounts', label: '模拟账户', icon: User },
  { path: '/market-data', label: '行情中心', icon: DataAnalysis },
  { path: '/orders', label: '模拟订单', icon: Tickets },
  { path: '/recovery-calculator', label: '回本计算', icon: Calculator },
]

const activePath = computed(() => {
  if (route.path === '/portfolios') return '/accounts'
  if (route.path === '/trades') return '/orders'
  if (route.path === '/positions' || route.path === '/cash-ledger') return '/market-data'
  return route.path
})

watch(
  isDarkMode,
  (enabled) => {
    document.documentElement.classList.toggle('dark', enabled)
    localStorage.setItem('qg-theme', enabled ? 'dark' : 'light')
    window.dispatchEvent(new CustomEvent('qg-theme-change'))
  },
  { immediate: true, flush: 'sync' },
)

async function toggleTheme(event: MouseEvent) {
  if (themeTransitioning.value) return

  const nextDarkMode = !isDarkMode.value
  const transitionDocument = document as ThemeTransitionDocument

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    isDarkMode.value = nextDarkMode
    return
  }

  const x = event.clientX
  const y = event.clientY
  const radius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))

  if (!transitionDocument.startViewTransition) {
    themeTransitioning.value = true
    themeOverlay.value = {
      x,
      y,
      scale: radius / 18,
      color: nextDarkMode ? '#171717' : '#f4f7fb',
      expanded: false,
      fading: false,
    }
    await nextTick()
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => {
        if (themeOverlay.value) themeOverlay.value.expanded = true
      })
    })
    window.setTimeout(() => {
      isDarkMode.value = nextDarkMode
      if (themeOverlay.value) themeOverlay.value.fading = true
      window.setTimeout(() => {
        themeOverlay.value = null
        themeTransitioning.value = false
      }, 180)
    }, 420)
    return
  }

  const transition = transitionDocument.startViewTransition(() => {
    isDarkMode.value = nextDarkMode
  })

  void transition.ready.then(() => {
    document.documentElement.animate(
      {
        clipPath: [`circle(0 at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`],
      },
      {
        duration: 420,
        easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
        pseudoElement: '::view-transition-new(root)',
      },
    )
  })
}
</script>

<style scoped>
.main-layout {
  width: 100%;
  min-width: 0;
  height: 100vh;
  overflow: hidden;
  --qg-sidebar-width: 218px;
}

.main-layout.is-sidebar-collapsed {
  --qg-sidebar-width: 68px;
}

.sidebar {
  min-width: 0;
  border-right: 1px solid var(--qg-border);
  color: var(--qg-text);
  background: var(--qg-sidebar-bg);
  overflow: hidden;
  transition: width 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.main-pane {
  min-width: 0;
  height: 100%;
}

.brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 18px;
}

.brand-name {
  max-width: 140px;
  overflow: hidden;
  color: var(--qg-text);
  font-size: 18px;
  font-weight: 700;
  opacity: 1;
  white-space: nowrap;
  transform: translateX(0);
  transition:
    max-width 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.16s ease,
    transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar-action {
  flex: 0 0 auto;
  color: var(--qg-text-muted);
}

.sidebar-action:hover {
  color: var(--qg-text);
  background: var(--qg-nav-hover);
}

.nav-menu {
  border-right: 0;
}

.nav-menu :deep(.el-menu-item) {
  gap: 12px;
  width: calc(100% - 20px);
  height: 44px;
  margin: 4px 10px;
  border-radius: 8px;
  color: var(--qg-nav-text);
  padding: 0 12px !important;
  transition:
    width 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    margin 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    padding 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    background-color 0.16s ease;
}

.nav-menu :deep(.el-icon) {
  flex: 0 0 auto;
  margin-right: 0;
  color: inherit;
  font-size: 18px;
}

.nav-menu :deep(.nav-label) {
  max-width: 120px;
  overflow: hidden;
  opacity: 1;
  white-space: nowrap;
  transition:
    max-width 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.16s ease;
}

.nav-menu :deep(.el-menu-item:hover) {
  background: var(--qg-nav-hover);
}

.nav-menu :deep(.el-menu-item.is-active) {
  color: var(--qg-text);
  background: var(--qg-nav-active);
}

.theme-section {
  margin-top: 4px;
}

.theme-toggle {
  display: flex;
  gap: 12px;
  align-items: center;
  width: calc(100% - 20px);
  height: 44px;
  margin: 4px 10px;
  padding: 0 12px;
  border: 0;
  border-radius: 8px;
  color: var(--qg-nav-text);
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: var(--el-menu-item-font-size, 14px);
  text-align: left;
}

.theme-toggle:hover {
  color: var(--qg-text);
  background: var(--qg-nav-hover);
}

.theme-toggle:disabled {
  cursor: default;
}

.theme-toggle .el-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin: 0;
  font-size: 18px;
  line-height: 1;
}

.theme-label {
  display: flex;
  align-items: center;
  height: 24px;
  max-width: 100px;
  overflow: hidden;
  line-height: 24px;
  opacity: 1;
  white-space: nowrap;
  transition:
    max-width 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.16s ease;
}

.is-sidebar-collapsed .brand {
  justify-content: center;
  padding: 0;
}

.is-sidebar-collapsed .brand-name {
  max-width: 0;
  opacity: 0;
  transform: translateX(-8px);
}

.is-sidebar-collapsed .sidebar-action {
  width: 30px;
}

.is-sidebar-collapsed .theme-toggle {
  justify-content: center;
  gap: 0;
  width: 44px;
  margin-right: 12px;
  margin-left: 12px;
  padding: 0;
}

.is-sidebar-collapsed .theme-label {
  max-width: 0;
  opacity: 0;
}

.is-sidebar-collapsed .nav-menu :deep(.el-menu-item) {
  gap: 0;
  justify-content: center;
  width: 44px;
  margin-right: 12px;
  margin-left: 12px;
  padding: 0 !important;
}

.is-sidebar-collapsed .nav-menu :deep(.nav-label) {
  max-width: 0;
  opacity: 0;
}

.is-sidebar-collapsed .nav-menu :deep(.el-menu-item .el-icon) {
  margin: 0;
}

.content {
  min-width: 0;
  overflow: auto;
  padding: 20px;
}

.theme-transition-overlay {
  position: fixed;
  z-index: 99999;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  opacity: 1;
  pointer-events: none;
  transform: scale(0);
  transform-origin: center;
  transition:
    transform 0.42s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.18s ease;
}

.theme-transition-overlay.is-expanded {
  transform: scale(var(--theme-overlay-scale));
}

.theme-transition-overlay.is-fading {
  opacity: 0;
}

@media (max-width: 1100px) {
  .main-layout:not(.is-sidebar-collapsed) {
    --qg-sidebar-width: 180px;
  }
}

@media (max-width: 760px) {
  .main-layout:not(.is-sidebar-collapsed) {
    --qg-sidebar-width: 156px;
  }

  .brand {
    padding: 0 14px;
  }

  .brand-name {
    font-size: 16px;
  }

  .nav-menu :deep(.el-menu-item) {
    margin-right: 8px;
    margin-left: 8px;
    font-size: 13px;
  }

  .content {
    padding: 14px;
  }
}
</style>
