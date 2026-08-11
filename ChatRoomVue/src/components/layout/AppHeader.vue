<!-- src/components/layout/AppHeader.vue — 顶部导航栏（亮色） -->
<template>
  <header class="flex items-center justify-between px-4 sm:px-6 h-14 shrink-0 z-10 header-glass"
          style="border-bottom: 1px solid var(--color-border)">
    <!-- 左侧：品牌 -->
    <RouterLink :to="authStore.isGuest ? '/' : '/chat'"
                class="text-base font-semibold tracking-wide transition-opacity hover:opacity-80"
                style="color: var(--color-ink)">
      Traum<span style="color: var(--color-signal)">Space</span>
    </RouterLink>

    <!-- 右侧 -->
    <div class="flex items-center gap-2 sm:gap-3">
      <template v-if="authStore.isAuthenticated">
        <!-- 管理员入口（文字 + 胶囊样式） -->
        <RouterLink v-if="authStore.isAdmin" to="/admin/users"
                    class="capsule-tag text-xs px-3 py-1 rounded-full font-medium">
          管理
        </RouterLink>

        <!-- 头像（普通用户可点击预览，游客仅展示） -->
        <div v-if="!authStore.isGuest" class="relative cursor-pointer group" title="点击查看头像"
             @click="showAvatarPreview = true">
          <UserAvatar :user="authStore.user" size="sm" />
          <div class="absolute inset-0 rounded-full bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
            <AppIcon name="eye" :size="14" class="text-white" />
          </div>
        </div>
        <UserAvatar v-if="authStore.isGuest" :user="authStore.user" size="sm" />

        <!-- 头像预览 -->
        <AvatarPreview ref="avatarPreviewRef"
                       :visible="showAvatarPreview"
                       :user="authStore.user"
                       @close="showAvatarPreview = false"
                       @change="handleAvatarChange"
                       @delete="handleAvatarDelete" />

        <!-- 用户名 -->
        <RouterLink v-if="!authStore.isGuest" to="/profile"
                    class="text-sm transition-colors hover:opacity-80" style="color: var(--color-ink-soft)">
          {{ authStore.displayName }}
        </RouterLink>
        <span v-if="authStore.isGuest" class="text-sm" style="color: var(--color-ink-faint)">
          {{ authStore.displayName }}
        </span>

        <!-- 登出 -->
        <n-button text :title="authStore.isGuest ? '退出游客' : '登出'" @click="handleLogout">
          <template #icon><AppIcon name="log-out" :size="16" /></template>
          <span class="hidden sm:inline">{{ authStore.isGuest ? '退出' : '登出' }}</span>
        </n-button>
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
import AppIcon from '@/components/ui/AppIcon.vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AvatarPreview from '@/components/user/AvatarPreview.vue'

const router = useRouter()
const authStore = useAuthStore()
const showAvatarPreview = ref(false)
const avatarPreviewRef = ref(null)

async function handleAvatarChange(blob) {
  try {
    const formData = new FormData()
    formData.append('file', blob, 'avatar.jpg')
    const res = await userApi.uploadAvatar(formData)
    if (res.data.code === 200) {
      // 缓存破坏：追加时间戳参数，强制浏览器重新加载
      authStore.user.avatar = res.data.data.avatarUrl + '?t=' + Date.now()
      authStore.user = { ...authStore.user }
      avatarPreviewRef.value?.uploadDone()
      window.$message?.success('头像更换成功')
    } else {
      avatarPreviewRef.value?.uploadFailed(res.data.message)
    }
  } catch (err) {
    console.error('头像上传失败', err)
    avatarPreviewRef.value?.uploadFailed(err.response?.data?.message || '头像上传失败')
  }
}

async function handleAvatarDelete() {
  try {
    const res = await userApi.deleteAvatar()
    if (res.data.code === 200) {
      authStore.user.avatar = null
      authStore.user = { ...authStore.user }
      window.$message?.success('已恢复默认头像')
    }
  } catch (err) {
    console.error('删除头像失败', err)
    window.$message?.error('删除头像失败')
  }
}

function handleLogout() {
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
