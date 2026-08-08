<template>
  <div class="app-shell" :class="{ 'is-desktop': desktopRuntime.isDesktop }">
    <DesktopTitleBar v-if="desktopRuntime.isDesktop" />
    <div class="app-content">
      <DesktopStartupView v-if="desktopRuntime.isDesktop && !desktopRuntime.isReady" />
      <RouterView v-else />
    </div>
  </div>
</template>

<script setup lang="ts">
import DesktopStartupView from '@/components/desktop/DesktopStartupView.vue'
import DesktopTitleBar from '@/components/desktop/DesktopTitleBar.vue'
import { desktopRuntime } from '@/desktop/runtime'
</script>

<style scoped>
.app-shell {
  display: grid;
  width: 100%;
  min-width: 0;
  height: 100%;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  overflow: hidden;
}

.app-shell.is-desktop {
  grid-template-rows: 40px minmax(0, 1fr);
}

.app-content {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
</style>
