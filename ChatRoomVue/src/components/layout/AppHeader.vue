<!-- src/components/layout/AppHeader.vue — 顶部导航栏（亮色） -->
<template>
  <!-- z-30：高于移动端侧边栏遮罩(z-20)，保证抽屉展开时顶栏依然清晰可见且可点击 -->
  <header class="flex items-center justify-between px-4 sm:px-6 h-14 shrink-0 z-30 header-glass"
          style="border-bottom: 1px solid var(--color-border)">
    <!-- 左侧：移动端侧边栏开关 + 品牌 -->
    <div class="flex items-center gap-2 sm:gap-3">
      <div v-if="showSidebarToggle" class="relative">
        <n-button quaternary circle size="small" @click="$emit('toggle-sidebar')"
                   aria-label="打开侧边栏">
          <template #icon><AppIcon name="menu" :size="18" /></template>
        </n-button>
        <!-- 侧边栏收起时的未读红点（私聊未读或好友申请） -->
        <span v-if="hasSidebarBadge"
              class="absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full"
              style="background:#FF3B30;box-shadow:0 0 0 2px var(--color-card)"></span>
      </div>

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

        <!-- 好友申请入口 -->
        <div v-if="!authStore.isGuest" class="flex items-center gap-1">
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
import { NButton } from 'naive-ui'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
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
const showAvatarPreview = ref(false)
const avatarPreviewRef = ref(null)
const showFriendRequests = ref(false)

// 侧边栏收起时菜单图标的未读红点：有私聊未读或好友申请时亮起
const hasSidebarBadge = computed(() =>
  chatStore.totalPrivateUnread > 0 || chatStore.friendRequestCount > 0
)

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
  // 断连、清 chat store、清 localStorage 全部收在 authStore.logout() 内
  await authStore.logout()
  // 跳转到首页，使用 replace 避免回退到已登出的页面
  router.replace('/')
}
</script>
