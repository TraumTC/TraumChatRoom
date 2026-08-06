<!-- src/components/friend/FriendList.vue — 好友列表（侧栏） -->
<template>
  <div class="flex flex-col h-full">
    <!-- 标题 + 操作 -->
    <div class="flex items-center justify-between px-3 py-2 border-b border-gray-200">
      <span class="text-xs text-gray-400 font-medium">好友</span>
      <div class="flex gap-1">
        <!-- 申请红点 -->
        <button class="relative p-1.5 rounded-full text-gray-400 hover:bg-gray-100 hover:text-gray-600"
                @click="showRequests = true" aria-label="好友申请">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
          <span v-if="pendingCount > 0"
                class="absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full bg-red-500"></span>
        </button>
        <button class="p-1.5 rounded-full text-gray-400 hover:bg-gray-100 hover:text-gray-600"
                @click="$emit('addFriend')" aria-label="添加好友">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 好友列表 -->
    <div class="flex-1 overflow-y-auto scroll-thin">
      <div v-if="loading" class="p-3">
        <div v-for="i in 3" :key="i" class="animate-pulse bg-gray-100 rounded h-12 mb-2"></div>
      </div>

      <div v-else-if="friends.length === 0" class="py-10 text-center text-sm text-gray-400">
        暂无好友
        <button class="block mx-auto mt-2 text-xs text-blue-500 hover:text-blue-600"
                @click="$emit('addFriend')">去添加</button>
      </div>

      <FriendItem v-for="friend in friends" :key="friend.id"
                  :friend="friend"
                  @open-chat="(f) => $emit('openChat', f)"
                  @deleted="loadFriends" />
    </div>

    <!-- 申请弹窗 -->
    <FriendRequest v-if="showRequests" @close="showRequests = false" @changed="loadFriends" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import FriendItem from './FriendItem.vue'
import FriendRequest from './FriendRequest.vue'
import { friendApi } from '@/api/friend'
import { useChatStore } from '@/stores/chat'

defineEmits(['openChat', 'addFriend'])
const chatStore = useChatStore()

const friends = ref([])
const loading = ref(false)
const showRequests = ref(false)
const pendingCount = ref(0)

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
      pendingCount.value = res.data.data.total
    }
  } catch (e) { /* 忽略 */ }
}

onMounted(() => {
  loadFriends()
  loadPendingCount()
})

onUnmounted(() => {
  // 清理
})
</script>
