<template>
  <div class="page-header">
    <div class="page-header-copy">
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div class="page-header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped>
.page-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
  flex-wrap: wrap;
}

.page-header-copy {
  min-width: 240px;
  flex: 1 1 360px;
}

.page-header-actions {
  display: flex;
  min-width: 0;
  flex: 0 1 auto;
  justify-content: flex-end;
}

.page-header-actions :deep(.el-space) {
  flex-wrap: wrap;
  justify-content: flex-end;
}

h1 {
  margin: 0;
  font-size: 22px;
}

p {
  margin: 6px 0 0;
  color: var(--qg-text-muted);
}

@media (max-width: 760px) {
  .page-header-actions,
  .page-header-actions :deep(.el-space) {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
