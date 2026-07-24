<template>
  <section class="not-found">
    <h1>404</h1>
    <p>页面不存在</p>
    <el-button type="primary" @click="$router.push('/dashboard')">返回总览</el-button>
  </section>
</template>

<style scoped>
.not-found {
  display: grid;
  min-height: 100vh;
  place-content: center;
  justify-items: center;
  gap: 12px;
}

h1 {
  margin: 0;
  font-size: 56px;
}

p {
  margin: 0;
  color: var(--qg-text-muted);
}
</style>
