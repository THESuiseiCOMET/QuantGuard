<template>
  <span>{{ formatted }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value?: string | number | null
}>()

const formatted = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return '暂无数据'
  const numeric = Number(props.value)
  if (!Number.isFinite(numeric)) return String(props.value)
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(numeric)
})
</script>
