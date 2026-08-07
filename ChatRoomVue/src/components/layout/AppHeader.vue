<!-- src/components/layout/AppHeader.vue — 顶部导航栏 -->
<template>
  <header class="h-14 bg-white border-b border-gray-200 flex items-center justify-between px-6 z-10 shrink-0">
    <!-- 左侧：品牌 -->
    <RouterLink :to="authStore.isGuest ? '/' : '/chat'"
                class="text-lg font-bold text-gray-900 hover:text-gray-700 transition-colors">
      TraumSpace
    </RouterLink>

    <!-- 右侧：用户信息 -->
    <div class="flex items-center gap-3">
      <template v-if="authStore.isAuthenticated">
        <!-- 管理员入口 -->
        <RouterLink v-if="authStore.isAdmin" to="/admin/users"
                    class="text-sm text-gray-500 hover:text-gray-900 mr-2">管理</RouterLink>

        <!-- 头像（普通用户可点击预览，游客仅展示） -->
        <div v-if="!authStore.isGuest" class="relative cursor-pointer group" title="点击查看头像"
             @click="showAvatarPreview = true">
          <UserAvatar :user="authStore.user" size="sm" />
          <div class="absolute inset-0 rounded-full bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
            <svg class="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
          </div>
        </div>
        <!-- 游客头像（仅展示，不可操作） -->
        <UserAvatar v-if="authStore.isGuest" :user="authStore.user" size="sm" />

        <!-- 头像预览模态框 -->
        <AvatarPreview :visible="showAvatarPreview"
                       :user="authStore.user"
                       @close="showAvatarPreview = false"
                       @change="handleAvatarChange"
                       @delete="handleAvatarDelete" />

        <!-- 用户名（可点击跳转个人中心，游客隐藏） -->
        <RouterLink v-if="!authStore.isGuest" to="/profile"
                    class="text-sm text-gray-700 hover:text-gray-900 transition-colors">
          {{ authStore.displayName }}
        </RouterLink>

        <!-- 游客名称 -->
        <span v-if="authStore.isGuest" class="text-sm text-gray-500">{{ authStore.displayName }}</span>

        <!-- 退出按钮 -->
        <button @click="handleLogout"
                class="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-500 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors"
                :title="authStore.isGuest ? '退出游客' : '登出'">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
          <span>{{ authStore.isGuest ? '退出' : '登出' }}</span>
        </button>
      </template>
    </div>
  </header>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useWebSocket } from '@/composables/useWebSocket'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AvatarPreview from '@/components/user/AvatarPreview.vue'

const router = useRouter()
const authStore = useAuthStore()
const showAvatarPreview = ref(false)

// 更换头像
async function handleAvatarChange(file) {
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await userApi.uploadAvatar(formData)
    if (res.data.code === 200) {
      authStore.user.avatar = res.data.data.avatarUrl
      authStore.user = { ...authStore.user }
    }
  } catch (err) {
    console.error('头像上传失败', err)
  }
}

// 删除头像
async function handleAvatarDelete() {
  try {
    const res = await userApi.deleteAvatar()
    if (res.data.code === 200) {
      authStore.user.avatar = null
      authStore.user = { ...authStore.user }
    }
  } catch (err) {
    console.error('删除头像失败', err)
  }
}

// 登出
function handleLogout() {
  // 先断开 WebSocket
  const { disconnect } = useWebSocket()
  disconnect()

  const refreshToken = localStorage.getItem('refreshToken')
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  authStore.accessToken = null
  authStore.user = null
  router.push('/')
  if (refreshToken) {
    authApi.logout({ refreshToken }).catch(() => {})
  }
}
</script>
