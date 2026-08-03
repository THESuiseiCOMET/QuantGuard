<template>
  <main class="desktop-startup">
    <section class="startup-content" aria-live="polite">
      <div class="brand">QuantGuard</div>

      <div v-if="!failed" class="startup-progress">
        <LoaderCircle class="spinner" :size="22" :stroke-width="1.8" />
        <div>
          <h1>{{ stageTitle }}</h1>
          <p>{{ stageMessage }}</p>
        </div>
      </div>

      <div v-else class="startup-failure">
        <h1>QuantGuard 启动失败</h1>
        <p v-if="failureStage">失败阶段：{{ failureStage }}</p>
        <p>{{ failureReason }}</p>
        <p class="log-path">日志目录：{{ snapshot?.logDirectory || '暂不可用' }}</p>
        <div class="startup-actions">
          <el-button type="primary" @click="retry">
            <RotateCcw :size="16" />
            重试启动
          </el-button>
          <el-button @click="openLogs">
            <FolderOpen :size="16" />
            打开日志
          </el-button>
          <el-button @click="exitApp">
            <Power :size="16" />
            退出应用
          </el-button>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FolderOpen, LoaderCircle, Power, RotateCcw } from '@lucide/vue'
import {
  desktopRuntime,
  exitDesktopApplication,
  openDesktopLogs,
  retryDesktopRuntime,
} from '@/desktop/runtime'

const snapshot = computed(() => desktopRuntime.snapshot)
const failed = computed(
  () => snapshot.value?.status === 'FAILED' || Boolean(desktopRuntime.bridgeError),
)
const failureReason = computed(
  () =>
    snapshot.value?.error ||
    desktopRuntime.bridgeError ||
    '本地服务未能完成启动，请查看日志后重试。',
)
const failureStage = computed(() => {
  switch (snapshot.value?.failureStage) {
    case 'Starting':
      return '运行环境准备'
    case 'PythonStarting':
      return '行情服务'
    case 'PythonReady':
    case 'BackendStarting':
      return '本地数据库和后端'
    default:
      return snapshot.value?.failureStage || ''
  }
})
const stageTitle = computed(() => {
  switch (snapshot.value?.status) {
    case 'PYTHON_STARTING':
      return '正在启动行情服务'
    case 'PYTHON_READY':
    case 'BACKEND_STARTING':
      return '正在启动本地数据库和后端'
    case 'READY':
      return 'QuantGuard 已就绪'
    case 'STOPPING':
      return '正在安全关闭本地服务'
    default:
      return '正在准备 QuantGuard'
  }
})
const stageMessage = computed(() => snapshot.value?.message || '正在检查桌面运行环境，请稍候。')

async function retry() {
  await retryDesktopRuntime()
}

async function openLogs() {
  await openDesktopLogs()
}

async function exitApp() {
  await exitDesktopApplication()
}
</script>

<style scoped>
.desktop-startup {
  display: grid;
  min-height: 100%;
  place-items: center;
  padding: 32px;
  background: var(--qg-bg);
  color: var(--qg-text);
}

.startup-content {
  width: min(560px, 100%);
}

.brand {
  margin-bottom: 40px;
  color: var(--qg-text);
  font-size: 20px;
  font-weight: 700;
}

.startup-progress {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 14px;
  align-items: start;
}

.spinner {
  margin-top: 3px;
  animation: spin 0.9s linear infinite;
}

h1 {
  margin: 0;
  font-size: 20px;
  line-height: 1.4;
}

p {
  margin: 8px 0 0;
  color: var(--qg-text-muted);
  font-size: 14px;
  line-height: 1.7;
}

.log-path {
  overflow-wrap: anywhere;
}

.startup-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}

.startup-actions :deep(.el-button) {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  margin-left: 0;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
