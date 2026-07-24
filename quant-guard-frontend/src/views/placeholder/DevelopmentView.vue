<template>
  <section class="page">
    <PageHeader :title="title" description="该模块不在本轮框架搭建范围内。" />
    <DevelopmentPlaceholder />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import DevelopmentPlaceholder from '@/components/common/DevelopmentPlaceholder.vue'

const route = useRoute()
const title = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : '开发中'))
</script>
