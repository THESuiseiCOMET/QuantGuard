<template>
  <section class="page">
    <PageHeader title="设置" />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />

    <section v-loading="loading" class="surface settings-panel">
      <header class="settings-header">
        <h2>Tushare Token</h2>
        <el-tag :type="config?.tokenConfigured ? 'success' : 'warning'" effect="plain">
          {{ config?.tokenConfigured ? 'Token 已配置' : 'Token 未配置' }}
        </el-tag>
      </header>

      <el-form class="token-form" @submit.prevent="saveToken">
        <el-input
          v-model="token"
          type="password"
          show-password
          aria-label="Tushare Token"
          placeholder="粘贴你的 Tushare Token"
          autocomplete="off"
          @keyup.enter="saveToken"
        />
        <div class="settings-actions">
          <el-button
            type="primary"
            :loading="saving"
            :disabled="token.trim().length === 0"
            @click="saveToken"
          >
            保存 Token
          </el-button>
        </div>
      </el-form>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getMarketDataProviderConfig, updateMarketDataProviderConfig } from '@/api/marketData'
import type { MarketDataProviderConfig } from '@/types/marketData'

const config = ref<MarketDataProviderConfig | null>(null)
const token = ref('')
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')

onMounted(() => {
  void loadConfig()
})

async function loadConfig() {
  loading.value = true
  errorMessage.value = ''
  try {
    config.value = await getMarketDataProviderConfig()
  } catch {
    errorMessage.value = '设置加载失败，请确认后端服务是否已启动。'
  } finally {
    loading.value = false
  }
}

async function saveToken() {
  const value = token.value.trim()
  if (!value || saving.value) return

  saving.value = true
  errorMessage.value = ''
  try {
    config.value = await updateMarketDataProviderConfig({ token: value })
    token.value = ''
    ElMessage.success('Token 已保存')
  } catch {
    errorMessage.value = 'Token 保存失败，请确认后端服务是否已启动。'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.settings-panel {
  width: min(100%, 560px);
  margin: 8px auto 0;
  padding: 22px;
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.settings-header h2 {
  margin: 0;
  font-size: 18px;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.token-form {
  width: 100%;
}

@media (max-width: 760px) {
  .settings-panel {
    margin-top: 0;
    padding: 16px;
  }
}
</style>
