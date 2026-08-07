<!-- src/views/ChatView.vue — 聊天室主页面 -->
<template>
  <div class="h-screen flex flex-col overflow-hidden bg-white">
    <!-- 顶部导航栏 -->
    <AppHeader />

    <!-- 主体区域 -->
    <div class="flex flex-1 min-h-0">
      <!-- 左侧栏 -->
      <aside class="w-64 shrink-0 bg-gray-50 border-r border-gray-200 flex flex-col">
        <!-- 好友列表（游客隐藏） -->
        <div v-if="!authStore.isGuest" class="border-b border-gray-200 flex-1 flex flex-col min-h-0">
          <FriendList @open-chat="startPrivateChat" @add-friend="showAddFriend = true" />
        </div>

        <!-- 在线用户 -->
        <div class="flex-1 overflow-y-auto scroll-thin min-h-0">
          <div class="p-4 pb-2 border-b border-gray-200">
            <div class="text-sm text-gray-500">
              在线人数：<span class="font-semibold text-gray-900">{{ onlineCount }}</span>
            </div>
          </div>
          <div class="px-3 py-1.5 text-xs text-gray-400 font-medium">在线用户</div>
          <div v-for="user in onlineUsers" :key="user.username"
               class="flex items-center gap-2 px-3 py-2 cursor-pointer hover:bg-gray-100 transition-colors"
               :class="{ 'bg-blue-50': isPrivateActive(user.name) }"
               @click="startPrivateChat(user)">
            <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
            <span class="flex-1 text-sm text-gray-800 truncate">{{ user.name }}</span>
            <!-- 私聊未读红点 -->
            <span v-if="hasPrivateUnread(user.name)"
                  class="min-w-4 h-4 px-1 rounded-full bg-red-500 text-white text-[10px] leading-none flex items-center justify-center">
              {{ getUnread(user.name) }}
            </span>
          </div>
          <div v-if="onlineUsers.length === 0" class="py-10 text-center text-sm text-gray-400">
            暂无在线用户
          </div>
        </div>
      </aside>

      <!-- 添加好友弹窗 -->
      <AddFriend v-if="showAddFriend" @close="showAddFriend = false" />

      <!-- 右侧 -->
      <main class="flex-1 flex flex-col min-w-0">
        <!-- 私聊标签条 -->
        <PrivateChatTab v-if="!authStore.isGuest" />

        <!-- 连接状态提示 -->
        <div v-if="!wsStore.connected" class="px-4 py-1 bg-amber-50 text-amber-600 text-xs text-center">
          {{ wsStore.connecting ? '正在连接...' : '连接已断开，正在重连...' }}
        </div>

        <!-- 会话标题 -->
        <div v-if="isPrivateMode" class="px-4 py-2 bg-white border-b border-gray-200 flex items-center gap-2">
          <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
          <span class="text-sm font-medium text-gray-900">{{ currentChat.name }}</span>
          <button @click="chatStore.openGroupChat()" class="ml-auto text-xs text-gray-400 hover:text-gray-600">
            返回群聊
          </button>
        </div>

        <!-- 聊天区 -->
        <div ref="messageArea" class="flex-1 overflow-y-auto scroll-thin py-2 bg-gray-50"
             @scroll="handleScroll">
          <!-- 加载更多 -->
          <div v-if="chatStore.loading" class="py-4 text-center text-sm text-gray-400">
            加载中...
          </div>
          <div v-else-if="!hasMore" class="py-4 text-center text-xs text-gray-400">
            没有更多消息了
          </div>

          <!-- 消息列表 -->
          <div v-if="displayMessages.length === 0" class="py-12 text-center text-sm text-gray-400">
            {{ isPrivateMode ? '开始私聊吧' : '还没有消息，来打个招呼吧' }}
          </div>
          <MessageItem v-for="msg in displayMessages" :key="msg.id" :message="msg" />
        </div>

        <!-- 输入区 -->
        <MessageInput :chat-type="isPrivateMode ? 'private' : 'group'"
                      :receiver="isPrivateMode ? currentChat.username : null"
                      @fileUpload="handleFileUpload" />
      </main>
    </div>

    <!-- Toast 通知 -->
    <div class="fixed top-4 right-4 z-60 flex flex-col gap-2">
      <div v-for="n in chatStore.notifications" :key="n.id"
           :class="['text-sm rounded-lg px-4 py-2 shadow-lg', toastClass(n.type)]">
        {{ n.message }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { useWebSocketStore } from '@/stores/websocket'
import { useWebSocket } from '@/composables/useWebSocket'
import { messageApi } from '@/api/message'
import { fileApi } from '@/api/file'
import AppHeader from '@/components/layout/AppHeader.vue'
import MessageItem from '@/components/chat/MessageItem.vue'
import MessageInput from '@/components/chat/MessageInput.vue'
import PrivateChatTab from '@/components/chat/PrivateChatTab.vue'
import FriendList from '@/components/friend/FriendList.vue'
import AddFriend from '@/components/friend/AddFriend.vue'

const chatStore = useChatStore()
const authStore = useAuthStore()
const wsStore = useWebSocketStore()
const { connect, disconnect } = useWebSocket()

const messageArea = ref(null)
const pageSize = 50
const hasMore = ref(true)
const showAddFriend = ref(false)
let unsubscribe = null

const messages = computed(() => chatStore.messages)
const onlineUsers = computed(() => chatStore.onlineUsers)
const onlineCount = computed(() => onlineUsers.value.length)
const currentChat = computed(() => chatStore.currentChat)
const isPrivateMode = computed(() => currentChat.value.type === 'private')

// 当前显示的消息（群聊或私聊）
const displayMessages = computed(() => {
  if (!isPrivateMode.value) return chatStore.messages
  const name = currentChat.value.name
  return chatStore.privateMessages[name] || []
})

function isPrivateActive(name) {
  return isPrivateMode.value && currentChat.value.name === name
}

function getUnread(name) {
  return chatStore.unreadCounts[name] || 0
}

function hasPrivateUnread(name) {
  return getUnread(name) > 0
}

function toastClass(type) {
  return {
    info: 'bg-gray-900/90 text-white',
    success: 'bg-emerald-500/90 text-white',
    error: 'bg-red-500/90 text-white'
  }[type] || 'bg-gray-900/90 text-white'
}

// 打开私聊并加载历史
async function startPrivateChat(user) {
  // 游客不能私聊
  if (authStore.isGuest) {
    chatStore.addNotification({ type: 'error', message: '游客不能发送私聊消息' })
    return
  }
  chatStore.openPrivateChat(user)

  // 加载私聊历史（如果没有）
  const name = user.name
  const username = user.username || user.name  // 优先用 username，兼容旧格式
  if (!chatStore.privateMessages[name] || chatStore.privateMessages[name].length === 0) {
    chatStore.setLoading(true)
    try {
      const res = await messageApi.getPrivateHistory(username, { size: pageSize })
      if (res.data.code === 200) {
        chatStore.setPrivateMessages(name, [...res.data.data.items].reverse())
      }
    } catch (e) {
      chatStore.setError('加载私聊历史失败')
    } finally {
      chatStore.setLoading(false)
    }
  }
}

// 加载历史消息（群聊）
async function loadHistory() {
  if (chatStore.loading || !hasMore.value || isPrivateMode.value) return
  chatStore.setLoading(true)
  try {
    const cursor = messages.value.length > 0 ? messages.value[0].id : null
    const res = await messageApi.getHistory({ cursor, size: pageSize })
    if (res.data.code === 200) {
      const data = res.data.data
      const oldHeight = messageArea.value?.scrollHeight || 0
      const oldScrollTop = messageArea.value?.scrollTop || 0

      chatStore.messages = [...data.items, ...chatStore.messages]

      await nextTick()
      if (messageArea.value) {
        messageArea.value.scrollTop = messageArea.value.scrollHeight - oldHeight + oldScrollTop
      }
      hasMore.value = data.hasMore
    }
  } catch (e) {
    chatStore.setError('加载历史消息失败')
  } finally {
    chatStore.setLoading(false)
  }
}

function handleScroll() {
  if (messageArea.value && messageArea.value.scrollTop < 50) {
    loadHistory()
  }
}

// 文件上传
async function handleFileUpload(file) {
  const type = file.type.startsWith('image/') ? 'image' : 'file'
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', type)
  // 私聊时附带接收者
  if (isPrivateMode.value) {
    formData.append('receiver', currentChat.value.name)
  }

  try {
    const res = await fileApi.upload(formData)
    if (res.data.code !== 200) {
      chatStore.addNotification({ type: 'error', message: res.data.message || '上传失败' })
    }
  } catch (e) {
    chatStore.addNotification({ type: 'error', message: '上传失败，请重试' })
  }
}

// 滚动到底部（新消息时）
function scrollToBottom() {
  nextTick(() => {
    if (messageArea.value) {
      messageArea.value.scrollTop = messageArea.value.scrollHeight
    }
  })
}

// 监听当前会话变化，切换时滚动到底部
watch(isPrivateMode, scrollToBottom)

onMounted(async () => {
  // 记录用户信息用于判断是否自己发的消息
  if (authStore.user?.id) {
    localStorage.setItem('myId', JSON.stringify(authStore.user.id))
  }
  if (authStore.user?.name) {
    localStorage.setItem('myName', authStore.user.name)
  }

  // 加载群聊最近消息
  chatStore.setLoading(true)
  try {
    const res = await messageApi.getHistory({ size: pageSize })
    if (res.data.code === 200) {
      const data = res.data.data
      chatStore.messages = [...data.items].reverse()
      hasMore.value = data.hasMore
      scrollToBottom()
    }
  } finally {
    chatStore.setLoading(false)
  }

  // 连接 WebSocket
  connect()

  // 新群聊消息自动滚动到底部
  unsubscribe = chatStore.$subscribe((mutation, state) => {
    if (mutation.storeId === 'chat' && mutation.type === 'direct' &&
        mutation.events.key === 'messages') {
      scrollToBottom()
    }
  })

  // 页面可见性监听（标题闪烁）
  document.addEventListener('visibilitychange', handleVisibility)
})

function handleVisibility() {
  chatStore.setPageHidden(document.hidden)
}

onUnmounted(() => {
  disconnect()
  if (unsubscribe) unsubscribe()
  document.removeEventListener('visibilitychange', handleVisibility)
  chatStore.stopTitleFlash()
})
</script>
