<template>
  <DesktopStartupView v-if="desktopRuntime.isDesktop && !desktopRuntime.isReady" />
  <RouterView v-else />
</template>

<script setup lang="ts">
import DesktopStartupView from '@/components/desktop/DesktopStartupView.vue'
import { desktopRuntime } from '@/desktop/runtime'
</script>
