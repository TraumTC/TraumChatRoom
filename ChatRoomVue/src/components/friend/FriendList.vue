<!-- src/components/friend/FriendList.vue — 好友列表（v-if/v-else 单一根节点，无 Fragment） -->
<template>
  <div class="flex flex-col h-full">
    <div class="flex items-center justify-between px-3 py-2" style="border-bottom: 1px solid var(--color-border)">
      <span class="text-xs font-medium" style="color: var(--color-ink-faint)">好友</span>
      <div class="flex gap-1">
        <div class="relative">
          <n-button quaternary circle size="small" @click="$emit('openRequests')" aria-label="好友申请">
            <template #icon><AppIcon name="bell" :size="15" /></template>
          </n-button>
          <span v-if="pendingCount > 0"
                class="absolute -top-0.5 -right-0.5 min-w-[16px] h-[16px] px-1 rounded-full text-[10px] font-medium text-white flex items-center justify-center"
                style="background: var(--color-alarm)">{{ pendingCount > 99 ? '99+' : pendingCount }}</span>
        </div>
        <n-button quaternary circle size="small" @click="$emit('addFriend')" aria-label="添加好友">
          <template #icon><AppIcon name="user-round-plus" :size="15" /></template>
        </n-button>
      </div>
    </div>

    <div class="flex-1 overflow-y-auto scroll-thin">
      <!-- 骨架屏 -->
      <div v-if="loading" class="p-3">
        <div v-for="i in 3" :key="i" class="rounded h-12 mb-2 animate-pulse"
             style="background: var(--color-hover)"></div>
      </div>

      <!-- 匥友列表 + 空状态（单一 div 包裹，无 Fragment） -->
      <div v-else>
        <FriendItem v-for="friend in friendsWithOnline" :key="friend.id"
                    :friend="friend"
                    @openChat="(f) => $emit('openChat', f)"
                    @deleted="loadFriends" />

        <div v-if="!loading && friends.length === 0" class="py-10 text-center text-sm"
             style="color: var(--color-ink-faint)">
          暂无好友
          <n-button text size="small" class="block mx-auto mt-2" style="color: var(--color-signal)"
                    @click="$emit('addFriend')">去添加</n-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { NButton } from 'naive-ui'
import { ref, computed, onMounted, watch } from 'vue'
import FriendItem from './FriendItem.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { friendApi } from '@/api/friend'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits(['openChat', 'addFriend', 'openRequests'])
const chatStore = useChatStore()
const authStore = useAuthStore()

const friends = ref([])
const loading = ref(false)

const pendingCount = computed(() => chatStore.friendRequestCount)

// 合并 API 数据与 WebSocket 实时在线状态
const friendsWithOnline = computed(() => {
  const onlineUsernames = new Set(
    (chatStore.onlineUsers || []).map(u => u.username)
  )
  return friends.value.map(f => ({
    ...f,
    online: onlineUsernames.has(f.username) || f.online
  }))
})

async function loadFriends() {
  loading.value = true
  try {
    const res = await friendApi.getList({ page: 1, size: 100 })
    if (res.data.code === 200) {
      friends.value = res.data.data.items || []
    }
  } catch (e) {
    console.error('[FriendList] loadFriends error:', e)
  } finally {
    loading.value = false
  }
}

async function loadPendingCount() {
  try {
    const res = await friendApi.getRequests({ type: 'received', status: 'pending' })
    if (res.data.code === 200) {
      // 优先用后端 total（真实总数）；缺失时降级用当页条数，避免 NaN
      const total = res.data.data?.total ?? res.data.data?.items?.length ?? 0
      chatStore.setFriendRequestCount(Number(total) || 0)
    }
  } catch (e) { /* 忽略 */ }
}

onMounted(() => {
  // 防御：无 token（登出中/未登录）时不发请求，避免 401
  if (authStore.isAuthenticated) {
    loadFriends()
    loadPendingCount()
  }
})

watch(() => chatStore.friendListVersion, () => {
  loadFriends()
  loadPendingCount()
})
</script>
