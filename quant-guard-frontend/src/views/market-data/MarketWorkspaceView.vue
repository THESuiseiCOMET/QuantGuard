<template>
  <div class="market-workspace">
    <MarketDataView />
    <section ref="positionsSectionRef" class="positions-section">
      <PositionsView embedded />
    </section>
  </div>
</template>

<script setup lang="ts">
/* global HTMLElement */
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import MarketDataView from '@/views/market-data/MarketDataView.vue'
import PositionsView from '@/views/positions/PositionsView.vue'

const route = useRoute()
const positionsSectionRef = ref<HTMLElement>()

onMounted(() => {
  scrollToPositionsIfNeeded()
})

watch(
  () => route.query.tab,
  () => {
    scrollToPositionsIfNeeded()
  },
)

function scrollToPositionsIfNeeded() {
  if (route.query.tab !== 'positions') return
  void nextTick(() => {
    positionsSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}
</script>

<style scoped>
.market-workspace {
  display: grid;
  gap: 22px;
  min-width: 0;
}

.positions-section {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding-top: 4px;
}
</style>
