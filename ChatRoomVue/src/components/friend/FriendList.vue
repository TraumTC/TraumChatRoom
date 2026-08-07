<!-- src/components/friend/FriendList.vue — 好友列表（亮色） -->
<template>
  <div class="flex flex-col h-full">
    <div class="flex items-center justify-between px-3 py-2" style="border-bottom: 1px solid var(--color-border)">
      <span class="text-xs font-medium" style="color: var(--color-ink-faint)">好友</span>
      <div class="flex gap-1">
        <n-button quaternary circle size="small" @click="showRequests = true" aria-label="好友申请">
          <template #icon><AppIcon name="user-plus" :size="15" /></template>
          <template v-if="pendingCount > 0">
            <span class="absolute top-0 right-0 w-2 h-2 rounded-full" style="background: var(--color-alarm)"></span>
          </template>
        </n-button>
        <n-button quaternary circle size="small" @click="$emit('addFriend')" aria-label="添加好友">
          <template #icon><AppIcon name="user-round-plus" :size="15" /></template>
        </n-button>
      </div>
    </div>

    <div class="flex-1 overflow-y-auto scroll-thin">
      <div v-if="loading" class="p-3">
        <div v-for="i in 3" :key="i" class="rounded h-12 mb-2 animate-pulse"
             style="background: var(--color-hover)"></div>
      </div>

      <div v-else-if="friends.length === 0" class="py-10 text-center text-sm" style="color: var(--color-ink-faint)">
        暂无好友
        <n-button text size="small" class="block mx-auto mt-2" style="color: var(--color-signal)"
                  @click="$emit('addFriend')">去添加</n-button>
      </div>

      <FriendItem v-for="friend in friends" :key="friend.id"
                  :friend="friend"
                  @openChat="(f) => $emit('openChat', f)"
                  @deleted="loadFriends" />
    </div>

    <FriendRequest v-if="showRequests" @close="showRequests = false" @changed="onRequestChanged" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import FriendItem from './FriendItem.vue'
import FriendRequest from './FriendRequest.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { friendApi } from '@/api/friend'
import { useChatStore } from '@/stores/chat'

defineEmits(['openChat', 'addFriend'])
const chatStore = useChatStore()

const friends = ref([])
const loading = ref(false)
const showRequests = ref(false)

const pendingCount = computed(() => chatStore.friendRequestCount)

async function loadFriends() {
  loading.value = true
  try {
    const res = await friendApi.getList({ page: 1, size: 100 })
    if (res.data.code === 200) {
      friends.value = res.data.data.items
    }
  } finally {
    loading.value = false
  }
}

async function loadPendingCount() {
  try {
    const res = await friendApi.getRequests({ type: 'received', status: 'pending' })
    if (res.data.code === 200) {
      chatStore.setFriendRequestCount(res.data.data.total)
    }
  } catch (e) { /* 忽略 */ }
}

function onRequestChanged() {
  loadPendingCount()
  loadFriends()
}

onMounted(() => {
  loadFriends()
  loadPendingCount()
})
</script>
