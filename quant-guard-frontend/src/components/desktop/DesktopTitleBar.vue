<template>
  <header class="desktop-titlebar" data-tauri-drag-region @dblclick.self="toggleMaximize">
    <div class="desktop-titlebar__brand" data-tauri-drag-region @dblclick="toggleMaximize">
      QuantGuard
    </div>

    <div class="desktop-titlebar__controls" aria-label="窗口控制">
      <button
        class="desktop-titlebar__button"
        type="button"
        title="最小化"
        aria-label="最小化"
        @click="minimize"
      >
        <Minus :size="16" :stroke-width="1.6" />
      </button>
      <button
        class="desktop-titlebar__button"
        type="button"
        :title="maximized ? '还原窗口' : '最大化'"
        :aria-label="maximized ? '还原窗口' : '最大化'"
        @click="toggleMaximize"
      >
        <Copy v-if="maximized" :size="14" :stroke-width="1.5" />
        <Square v-else :size="13" :stroke-width="1.5" />
      </button>
      <button
        class="desktop-titlebar__button desktop-titlebar__button--close"
        type="button"
        title="关闭"
        aria-label="关闭"
        @click="close"
      >
        <X :size="17" :stroke-width="1.5" />
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Copy, Minus, Square, X } from '@lucide/vue'
import type { Window } from '@tauri-apps/api/window'

const maximized = ref(false)
let appWindow: Window | undefined
let stopResizeListener: (() => void) | undefined

onMounted(async () => {
  const { getCurrentWindow } = await import('@tauri-apps/api/window')
  const currentWindow = getCurrentWindow()
  appWindow = currentWindow
  maximized.value = await currentWindow.isMaximized()
  stopResizeListener = await currentWindow.onResized(async () => {
    maximized.value = await currentWindow.isMaximized()
  })
})

onBeforeUnmount(() => {
  stopResizeListener?.()
})

async function minimize() {
  await appWindow?.minimize()
}

async function toggleMaximize() {
  await appWindow?.toggleMaximize()
  maximized.value = (await appWindow?.isMaximized()) ?? false
}

async function close() {
  await appWindow?.close()
}
</script>

<style scoped>
.desktop-titlebar {
  position: relative;
  z-index: 5000;
  display: flex;
  min-width: 0;
  height: 40px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--qg-border);
  color: var(--qg-text);
  background: var(--qg-sidebar-bg);
  user-select: none;
}

.desktop-titlebar__brand {
  display: flex;
  min-width: 0;
  height: 100%;
  flex: 1;
  align-items: center;
  padding: 0 14px;
  font-size: 14px;
  font-weight: 600;
}

.desktop-titlebar__controls {
  display: flex;
  height: 100%;
  flex: 0 0 auto;
}

.desktop-titlebar__button {
  display: inline-flex;
  width: 46px;
  height: 40px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 0;
  color: var(--qg-text);
  background: transparent;
  cursor: default;
}

.desktop-titlebar__button:hover {
  background: var(--qg-nav-hover);
}

.desktop-titlebar__button:focus-visible {
  outline: 2px solid var(--qg-text-muted);
  outline-offset: -2px;
}

.desktop-titlebar__button--close:hover,
.desktop-titlebar__button--close:focus-visible {
  color: #ffffff;
  background: #c42b1c;
}
</style>
