<!-- src/components/layout/AppHeader.vue — 顶部导航栏（亮色） -->
<template>
  <header class="flex items-center justify-between px-4 sm:px-6 h-14 shrink-0 z-10 header-glass"
          style="border-bottom: 1px solid var(--color-border)">
    <!-- 左侧：移动端侧边栏开关 + 品牌 -->
    <div class="flex items-center gap-2 sm:gap-3">
      <n-button v-if="showSidebarToggle" quaternary circle size="small" @click="$emit('toggle-sidebar')"
                 aria-label="打开侧边栏">
        <template #icon><AppIcon name="menu" :size="18" /></template>
      </n-button>

      <RouterLink :to="authStore.isGuest ? '/' : '/chat'"
                  class="text-base font-semibold tracking-wide transition-opacity hover:opacity-80"
                  style="color: var(--color-ink)">
        Traum<span style="color: var(--color-signal)">Space</span>
      </RouterLink>
    </div>

    <!-- 右侧 -->
    <div class="flex items-center gap-2 sm:gap-3">
      <template v-if="authStore.isAuthenticated">
        <!-- 管理员入口（文字 + 胶囊样式） -->
        <RouterLink v-if="authStore.isAdmin" to="/admin/users"
                    class="capsule-tag text-xs px-3 py-1 rounded-full font-medium">
          管理
        </RouterLink>

        <!-- 全局通知入口 -->
        <div v-if="!authStore.isGuest" class="flex items-center gap-1">
          <!-- 私聊未读 -->
          <div v-if="chatStore.totalPrivateUnread > 0" class="relative cursor-pointer"
               @click="openFirstPrivateUnread" title="未读私聊消息">
            <n-button quaternary circle size="small">
              <template #icon><AppIcon name="message" :size="16" /></template>
            </n-button>
            <span class="absolute -top-0.5 -right-0.5 min-w-[16px] h-[16px] px-1 rounded-full text-[10px] font-medium text-white flex items-center justify-center"
                  style="background: var(--color-alarm)">{{ chatStore.totalPrivateUnread > 99 ? '99+' : chatStore.totalPrivateUnread }}</span>
          </div>

          <!-- 好友申请 -->
          <div v-if="chatStore.friendRequestCount > 0" class="relative cursor-pointer"
               @click="showFriendRequests = true" title="好友申请">
            <n-button quaternary circle size="small">
              <template #icon><AppIcon name="bell" :size="16" /></template>
            </n-button>
            <span class="absolute -top-0.5 -right-0.5 min-w-[16px] h-[16px] px-1 rounded-full text-[10px] font-medium text-white flex items-center justify-center"
                  style="background: var(--color-alarm)">{{ chatStore.friendRequestCount > 99 ? '99+' : chatStore.friendRequestCount }}</span>
          </div>
        </div>

        <!-- 头像（普通用户可点击预览，游客仅展示） -->
        <div v-if="!authStore.isGuest" class="relative cursor-pointer group" title="点击查看头像"
             @click="showAvatarPreview = true">
          <UserAvatar :user="authStore.user" size="sm" />
          <div class="absolute inset-0 rounded-full bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
            <AppIcon name="eye" :size="14" class="text-white" />
          </div>
        </div>
        <UserAvatar v-if="authStore.isGuest" :user="authStore.user" size="sm" />

        <!-- 好友申请弹窗 -->
        <FriendRequest v-if="showFriendRequests" @close="showFriendRequests = false" @changed="onFriendRequestChanged" />

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
import { useChatStore } from '@/stores/chat'
import { useWebSocket } from '@/composables/useWebSocket'
import { userApi } from '@/api/user'
import AppIcon from '@/components/ui/AppIcon.vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AvatarPreview from '@/components/user/AvatarPreview.vue'
import FriendRequest from '@/components/friend/FriendRequest.vue'

const props = defineProps({
  showSidebarToggle: { type: Boolean, default: false }
})
const emit = defineEmits(['toggle-sidebar'])

const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()
const { disconnect } = useWebSocket()
const showAvatarPreview = ref(false)
const avatarPreviewRef = ref(null)
const showFriendRequests = ref(false)

// 打开最早未读的私聊会话
function openFirstPrivateUnread() {
  const entries = Object.entries(chatStore.unreadCounts)
    .filter(([_, count]) => count > 0)
  if (entries.length === 0) return
  // 取第一个有未读的会话
  const [username] = entries[0]
  const senderInfo = chatStore.privateUnreadSenders[username]
  chatStore.openPrivateChat({
    username,
    name: senderInfo?.name || username,
    id: senderInfo?.id || null
  })
}

function onFriendRequestChanged() {
  chatStore.incrementFriendListVersion()
}

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

async function handleLogout() {
  disconnect()
  // 清理所有本地状态（包括缓存的用户信息）
  chatStore.clearMessages()
  await authStore.logout()
  // 跳转到首页，使用 replace 避免回退到已登出的页面
  router.replace('/')
}
</script>
