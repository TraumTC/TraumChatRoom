<!-- src/components/admin/AdminTabs.vue — 管理后台 tab 导航（亮色） -->
<template>
  <div class="flex items-center justify-between mb-5" style="border-bottom: 1px solid var(--color-border)">
    <div class="flex items-center gap-1">
      <div v-for="t in tabs" :key="t.path"
           class="px-4 py-2 text-sm cursor-pointer select-none"
           :style="{ color: isActive(t.path) ? 'var(--color-signal)' : 'var(--color-ink-soft)',
                     borderBottom: '2px solid ' + (isActive(t.path) ? 'var(--color-signal)' : 'transparent'),
                     fontWeight: isActive(t.path) ? '600' : '400',
                     marginBottom: '-1px',
                     transition: 'color 0.2s, border-color 0.2s' }"
           @click="router.push(t.path)">
        {{ t.label }}
      </div>
    </div>

    <!-- 返回群聊按钮 -->
    <n-button size="small" quaternary @click="router.push('/chat')" class="mb-1">
      <template #icon><AppIcon name="arrow-left" :size="14" /></template>
      返回群聊
    </n-button>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'

const route = useRoute()
const router = useRouter()

const tabs = [
  { path: '/admin/users', label: '用户管理' },
  { path: '/admin/sensitive-words', label: '敏感词管理' },
  { path: '/admin/logs', label: '操作日志' }
]

function isActive(path) {
  return route.path === path
}
</script>
