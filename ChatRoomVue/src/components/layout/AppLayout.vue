<!-- src/components/layout/AppLayout.vue — 页面布局容器 -->
<template>
  <div class="min-h-screen bg-white">
    <AppHeader />
    <main class="flex-1">
      <slot />
    </main>
  </div>
</template>

<script setup>
import AppHeader from './AppHeader.vue'
</script>
