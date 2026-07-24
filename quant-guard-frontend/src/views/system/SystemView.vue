<template>
  <section class="page">
    <PageHeader title="系统信息" description="前后端分离开发环境状态。" />
    <div class="surface info-list">
      <div>
        <span>前端开发端口</span>
        <strong>5173</strong>
      </div>
      <div>
        <span>后端默认端口</span>
        <strong>8080</strong>
      </div>
      <div>
        <span>API 代理</span>
        <strong>/api -> http://localhost:8080</strong>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader.vue'
</script>

<style scoped>
.info-list {
  display: grid;
  gap: 12px;
  padding: 20px;
}

.info-list div {
  display: flex;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--qg-border);
}

.info-list div:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

span {
  color: var(--qg-text-muted);
}
</style>
