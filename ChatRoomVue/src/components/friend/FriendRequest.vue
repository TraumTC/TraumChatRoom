<!-- src/components/friend/FriendRequest.vue — 好友申请列表弹窗 -->
<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4">
    <div class="fixed inset-0 bg-black/40 z-40" @click="$emit('close')"></div>
    <div class="bg-white rounded-lg shadow-lg w-full max-w-md relative z-50 max-h-[80vh] flex flex-col">
      <div class="px-5 pt-5 text-lg font-semibold text-gray-900 flex items-center justify-between">
        好友申请
        <button class="text-gray-400 hover:text-gray-600" @click="$emit('close')" aria-label="关闭">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- 分类切换 -->
      <div class="px-5 pt-3 flex gap-4 border-b border-gray-100">
        <button :class="['pb-2 text-sm', type === 'received' ? 'text-blue-600 border-b-2 border-blue-500' : 'text-gray-400']"
                @click="switchType('received')">收到的申请</button>
        <button :class="['pb-2 text-sm', type === 'sent' ? 'text-blue-600 border-b-2 border-blue-500' : 'text-gray-400']"
                @click="switchType('sent')">发出的申请</button>
      </div>

      <!-- 列表 -->
      <div class="flex-1 overflow-y-auto scroll-thin px-5 py-4">
        <div v-if="loading" class="py-8 text-center text-sm text-gray-400">加载中...</div>
        <div v-else-if="items.length === 0" class="py-8 text-center text-sm text-gray-400">
          暂无申请
        </div>

        <div v-for="item in items" :key="item.id"
             class="flex items-center gap-3 px-3 py-2 hover:bg-gray-50 rounded-lg">
          <UserAvatar :user="type === 'received' ? item.sender : item.receiver" size="md" />
          <div class="flex-1 min-w-0">
            <div class="text-sm text-gray-900 truncate">
              {{ (type === 'received' ? item.sender : item.receiver)?.name }}
            </div>
            <div class="text-xs text-gray-400 truncate">{{ item.message || '无附言' }}</div>
          </div>

          <!-- 状态 + 操作 -->
          <template v-if="item.status === 'accepted'">
            <span class="text-xs text-gray-400">已同意</span>
            <button class="text-xs text-gray-300 hover:text-red-500 ml-1" @click="deleteRequest(item.id)" title="删除记录">×</button>
          </template>
          <template v-else-if="item.status === 'rejected'">
            <span class="text-xs text-gray-400">已拒绝</span>
            <button class="text-xs text-gray-300 hover:text-red-500 ml-1" @click="deleteRequest(item.id)" title="删除记录">×</button>
          </template>
          <template v-else-if="item.status === 'expired'">
            <span class="text-xs text-gray-400">已过期</span>
            <button class="text-xs text-gray-300 hover:text-red-500 ml-1" @click="deleteRequest(item.id)" title="删除记录">×</button>
          </template>

          <!-- 收到的待处理申请：同意/拒绝 -->
          <template v-else-if="type === 'received'">
            <button class="px-3 py-1 text-xs bg-emerald-500 text-white rounded hover:bg-emerald-600"
                    @click="handleRequest(item.id, 'accept')">同意</button>
            <button class="px-3 py-1 text-xs bg-white border border-gray-200 text-gray-600 rounded hover:bg-gray-50"
                    @click="handleRequest(item.id, 'reject')">拒绝</button>
          </template>

          <!-- 发出的待处理申请 -->
          <span v-else class="text-xs text-amber-500">等待处理</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import { friendApi } from '@/api/friend'
import { useChatStore } from '@/stores/chat'

const emit = defineEmits(['close', 'changed'])
const chatStore = useChatStore()

const type = ref('received')
const items = ref([])
const loading = ref(false)

async function loadRequests() {
  loading.value = true
  try {
    const res = await friendApi.getRequests({ type: type.value, page: 1, size: 50 })
    if (res.data.code === 200) {
      items.value = res.data.data.items
    }
  } finally {
    loading.value = false
  }
}

function switchType(t) {
  type.value = t
  loadRequests()
}

async function handleRequest(id, action) {
  try {
    const res = await friendApi.handleRequest(id, { action })
    if (res.data.code === 200) {
      chatStore.addNotification({
        type: 'success',
        message: action === 'accept' ? '已同意' : '已拒绝'
      })
      loadRequests()
      emit('changed')
    } else {
      chatStore.addNotification({ type: 'error', message: res.data.message })
    }
  } catch (e) {
    chatStore.addNotification({ type: 'error', message: e.response?.data?.message || '操作失败' })
  }
}

async function deleteRequest(id) {
  try {
    const res = await friendApi.deleteRequest(id)
    if (res.data.code === 200) {
      loadRequests()
      emit('changed')
    } else {
      chatStore.addNotification({ type: 'error', message: res.data.message })
    }
  } catch (e) {
    chatStore.addNotification({ type: 'error', message: e.response?.data?.message || '删除失败' })
  }
}

onMounted(loadRequests)
</script>
