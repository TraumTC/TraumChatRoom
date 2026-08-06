<!-- src/components/layout/AppHeader.vue — 顶部导航栏 -->
<template>
  <header class="h-14 bg-white border-b border-gray-200 flex items-center justify-between px-4 z-10">
    <div class="flex items-center gap-3">
      <RouterLink to="/" class="text-lg font-bold text-gray-900">TraumChatRoom</RouterLink>
      <RouterLink to="/chat" class="text-sm text-gray-500 hover:text-gray-900">聊天室</RouterLink>
    </div>

    <div class="flex items-center gap-4">
      <!-- 管理员后台 -->
      <template v-if="authStore.isAdmin">
        <RouterLink to="/admin/users" class="text-sm text-gray-500 hover:text-gray-900">用户管理</RouterLink>
        <RouterLink to="/admin/sensitive-words" class="text-sm text-gray-500 hover:text-gray-900">敏感词</RouterLink>
        <RouterLink to="/admin/logs" class="text-sm text-gray-500 hover:text-gray-900">操作日志</RouterLink>
      </template>

      <!-- 个人中心（游客隐藏） -->
      <RouterLink v-if="!authStore.isGuest" to="/profile"
                  class="text-sm text-gray-500 hover:text-gray-900">个人中心</RouterLink>

      <!-- 用户信息 + 登出 -->
      <template v-if="authStore.isAuthenticated">
        <span class="text-sm text-gray-700">{{ authStore.displayName }}</span>
        <button @click="handleLogout" class="text-sm text-gray-400 hover:text-gray-600">
          {{ authStore.isGuest ? '退出游客' : '登出' }}
        </button>
      </template>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

async function handleLogout() {
  await authStore.logout()
  router.push('/')
}
</script>
