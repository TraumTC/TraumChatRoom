<!-- src/components/friend/AddFriend.vue — 添加好友弹窗 -->
<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4">
    <div class="fixed inset-0 bg-black/40 z-40" @click="$emit('close')"></div>
    <div class="bg-white rounded-lg shadow-lg w-full max-w-md relative z-50">
      <div class="px-5 pt-5 text-lg font-semibold text-gray-900">添加好友</div>

      <!-- 搜索框 -->
      <div class="px-5 py-4">
        <input v-model="keyword" placeholder="输入用户名或昵称搜索"
               class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
               @input="debouncedSearch" />
      </div>

      <!-- 搜索结果 -->
      <div class="px-5 pb-4 max-h-64 overflow-y-auto scroll-thin">
        <div v-if="searching" class="py-8 text-center text-sm text-gray-400">搜索中...</div>
        <div v-else-if="results.length === 0 && keyword" class="py-8 text-center text-sm text-gray-400">
          未找到用户
        </div>

        <div v-for="user in results" :key="user.id"
             class="flex items-center gap-3 px-3 py-2 hover:bg-gray-50 rounded-lg">
          <UserAvatar :user="user" size="md" />
          <div class="flex-1 min-w-0">
            <div class="text-sm text-gray-900 truncate">{{ user.name }}</div>
            <div class="text-xs text-gray-400">{{ user.username }}</div>
          </div>

          <!-- 状态按钮 -->
          <button v-if="user.friendStatus === 'none'"
                  class="px-3 py-1 text-xs bg-blue-500 text-white rounded hover:bg-blue-600"
                  @click="showApply(user)">
            添加
          </button>
          <span v-else-if="user.friendStatus === 'friend'" class="text-xs text-gray-400">已是好友</span>
          <span v-else-if="user.friendStatus === 'pending_sent'" class="text-xs text-amber-500">已发送</span>
          <button v-else-if="user.friendStatus === 'pending_received'"
                  class="px-3 py-1 text-xs bg-emerald-500 text-white rounded hover:bg-emerald-600"
                  @click="handleAccept(user)">
            同意
          </button>
        </div>
      </div>

      <!-- 申请附言弹窗 -->
      <div v-if="applyTarget" class="fixed inset-0 z-60 flex items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/40 z-40" @click="applyTarget = null"></div>
        <div class="bg-white rounded-lg shadow-lg w-full max-w-sm relative z-50">
          <div class="px-5 pt-5 text-lg font-semibold text-gray-900">
            添加 {{ applyTarget.name }}
          </div>
          <div class="px-5 py-4">
            <textarea v-model="applyMessage" placeholder="附言（可选，最多100字）" maxlength="100" rows="3"
                      class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
          </div>
          <div class="px-5 pb-5 pt-2 flex justify-end gap-2">
            <button class="px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 rounded" @click="applyTarget = null">取消</button>
            <button class="px-4 py-2 text-sm bg-blue-500 text-white rounded hover:bg-blue-600" @click="sendApply">
              发送申请
            </button>
          </div>
        </div>
      </div>

      <!-- 关闭 -->
      <button class="absolute top-3 right-3 text-gray-400 hover:text-gray-600" @click="$emit('close')" aria-label="关闭">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import { friendApi } from '@/api/friend'
import { clearRequestId } from '@/utils/request-id'
import { useChatStore } from '@/stores/chat'

const emit = defineEmits(['close', 'added'])
const chatStore = useChatStore()

const keyword = ref('')
const results = ref([])
const searching = ref(false)
const applyTarget = ref(null)
const applyMessage = ref('')

let timer = null
function debouncedSearch() {
  clearTimeout(timer)
  if (!keyword.value.trim()) {
    results.value = []
    return
  }
  timer = setTimeout(search, 300)
}

async function search() {
  searching.value = true
  try {
    const res = await friendApi.search(keyword.value.trim())
    if (res.data.code === 200) {
      results.value = res.data.data
    }
  } finally {
    searching.value = false
  }
}

function showApply(user) {
  applyTarget.value = user
  applyMessage.value = ''
}

// 发送好友申请（X-Request-Id 防重复提交，API 层已内置）
async function sendApply() {
  try {
    const res = await friendApi.sendRequest({
      receiverId: applyTarget.value.id,
      message: applyMessage.value
    })
    if (res.data.code === 200) {
      clearRequestId('friend-request')
      chatStore.addNotification({ type: 'success', message: '申请已发送' })
      applyTarget.value = null
      search()
    } else {
      chatStore.addNotification({ type: 'error', message: res.data.message })
    }
  } catch (e) {
    chatStore.addNotification({ type: 'error', message: e.response?.data?.message || '发送失败' })
  }
}

// 处理收到的申请
async function handleAccept(user) {
  try {
    const res = await friendApi.getRequests({ type: 'received', status: 'pending' })
    if (res.data.code === 200) {
      const request = res.data.data.items.find(r => r.sender?.id === user.id)
      if (request) {
        await friendApi.handleRequest(request.id, { action: 'accept' })
        chatStore.addNotification({ type: 'success', message: `已添加 ${user.name}` })
        search()
        emit('added')
      }
    }
  } catch (e) {
    chatStore.addNotification({ type: 'error', message: e.response?.data?.message || '操作失败' })
  }
}
</script>
