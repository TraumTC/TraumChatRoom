<!-- src/components/admin/AdminTabs.vue — 管理后台 tab 导航（亮色） -->
<template>
  <div class="flex items-center gap-1 mb-5" style="border-bottom: 1px solid var(--color-border)">
    <RouterLink v-for="t in tabs" :key="t.path" :to="t.path"
                class="px-4 py-2 text-sm transition-colors"
                :class="isActive(t.path) ? 'is-active' : ''"
                :style="isActive(t.path) ? 'color: var(--color-signal); border-bottom: 2px solid var(--color-signal); font-weight: 600'
                                         : 'color: var(--color-ink-soft)'">
      {{ t.label }}
    </RouterLink>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()

const tabs = [
  { path: '/admin/users', label: '用户管理' },
  { path: '/admin/sensitive-words', label: '敏感词管理' },
  { path: '/admin/logs', label: '操作日志' }
]

function isActive(path) {
  return route.path === path
}
</script>
