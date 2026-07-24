<template>
  <div class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <slot name="actions" />
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
}

h1 {
  margin: 0;
  font-size: 22px;
}

p {
  margin: 6px 0 0;
  color: var(--qg-text-muted);
}
</style>
