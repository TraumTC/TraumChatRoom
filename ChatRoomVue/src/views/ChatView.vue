<!-- src/views/ChatView.vue — 聊天室主页面（亮色） -->
<template>
  <div class="h-screen flex flex-col overflow-hidden" style="background: var(--color-bg)">
    <AppHeader />

    <div class="flex flex-1 min-h-0">
      <!-- 移动端遮罩 -->
      <div v-if="sidebarOpen && isMobile" class="fixed inset-0 z-20" style="background: rgba(0,0,0,0.5)" @click="sidebarOpen = false"></div>

      <!-- 左侧栏 -->
      <aside :class="['w-64 shrink-0 flex flex-col', isMobile ? 'fixed z-30 left-0 top-14 bottom-0 transition-transform duration-200' : '']"
             :style="isMobile ? { transform: sidebarOpen ? 'translateX(0)' : 'translateX(-100%)', background: 'var(--color-ghost)', borderRight: '1px solid var(--color-border)' }
                                 : { background: 'var(--color-ghost)', borderRight: '1px solid var(--color-border)' }">
        <!-- 好友列表（游客隐藏） -->
        <div v-if="!authStore.isGuest" class="flex-1 flex flex-col min-h-0" style="border-bottom: 1px solid var(--color-border)">
          <FriendList @openChat="startPrivateChat" @addFriend="showAddFriend = true" />
        </div>

        <!-- 在线用户 -->
        <div class="flex-1 overflow-y-auto scroll-thin min-h-0">
          <div class="p-4 pb-2 flex items-center gap-2" style="border-bottom: 1px solid var(--color-border)">
            <span class="signal-dot"></span>
            <span class="text-sm tabular" style="color: var(--color-ink-soft)">
              在线 <span style="color: var(--color-ink); font-weight: 600">{{ onlineCount }}</span>
            </span>
          </div>
          <div class="px-3 py-1dot5 text-xs font-medium" style="color: var(--color-ink-faint)">在线用户</div>
          <div v-for="user in onlineUsers" :key="user.username"
               class="flex items-center gap-2 px-3 py-2 cursor-pointer transition-colors"
               :class="{ 'is-active': isPrivateActive(user.username) }"
               @click="startPrivateChat(user)">
            <span class="signal-dot"></span>
            <span class="flex-1 text-sm truncate" style="color: var(--color-ink)">{{ user.name }}</span>
            <span v-if="hasPrivateUnread(user.username)"
                  class="min-w-4 h-4 px-1 rounded-full text-white text-[10px] leading-none flex items-center justify-center tabular"
                  style="background: var(--color-alarm)">
              {{ getUnread(user.username) }}
            </span>
          </div>
          <div v-if="onlineUsers.length === 0" class="py-10 text-center text-sm" style="color: var(--color-ink-faint)">
            暂无在线用户
          </div>
        </div>
      </aside>

      <!-- 添加好友弹窗 -->
      <AddFriend v-if="showAddFriend" @close="showAddFriend = false" />

      <!-- 右侧 -->
      <main class="flex-1 flex flex-col min-w-0">
        <!-- 私聊标签条 -->
        <PrivateChatTab v-if="!authStore.isGuest" @open-group="chatStore.openGroupChat" />

        <!-- 移动端折叠开关 -->
        <button v-if="isMobile" @click="sidebarOpen = true"
                class="px-3 py-1 text-xs self-start" style="color: var(--color-ink-soft)">
          <AppIcon name="panel-left" :size="14" />
        </button>

        <!-- 连接状态提示 -->
        <div v-if="!wsStore.connected" class="px-4 py-1 text-xs text-center"
             style="background: rgba(59,130,246,0.08); color: var(--color-signal)">
          {{ wsStore.connecting ? '正在连接...' : '连接已断开，正在重连...' }}
        </div>

        <!-- 会话标题 -->
        <div v-if="isPrivateMode" class="px-4 py-2 flex items-center gap-2"
             style="background: var(--color-card); border-bottom: 1px solid var(--color-border)">
          <span class="signal-dot"></span>
          <span class="text-sm font-medium" style="color: var(--color-ink)">{{ currentChat.name }}</span>
          <button @click="chatStore.openGroupChat()" class="ml-auto text-xs transition-colors"
                  style="color: var(--color-ink-faint)">
            返回群聊
          </button>
        </div>

        <!-- 聊天区 -->
        <div ref="messageArea" class="flex-1 overflow-y-auto scroll-thin py-2" @scroll="handleScroll"
             style="background: var(--color-bg)">
          <div v-if="chatStore.loading" class="py-4 text-center text-sm" style="color: var(--color-ink-faint)">
            加载中...
          </div>
          <div v-else-if="!hasMore" class="py-4 text-center text-xs" style="color: var(--color-ink-faint)">
            没有更多消息了
          </div>

          <div v-if="displayMessages.length === 0" class="py-12 text-center text-sm" style="color: var(--color-ink-faint)">
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
import AppIcon from '@/components/ui/AppIcon.vue'
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
const sidebarOpen = ref(false)
const isMobile = ref(window.innerWidth < 1024)
let unsubscribe = null

const messages = computed(() => chatStore.messages)
const onlineUsers = computed(() => chatStore.onlineUsers)
const onlineCount = computed(() => onlineUsers.value.length)
const currentChat = computed(() => chatStore.currentChat)
const isPrivateMode = computed(() => currentChat.value.type === 'private')

// 当前显示的消息（群聊或私聊，私聊 key 用 username）
const displayMessages = computed(() => {
  if (!isPrivateMode.value) return chatStore.messages
  const username = currentChat.value.username
  return chatStore.privateMessages[username] || []
})

function isPrivateActive(username) {
  return isPrivateMode.value && currentChat.value.username === username
}

function getUnread(username) {
  return chatStore.unreadCounts[username] || 0
}

function hasPrivateUnread(username) {
  return getUnread(username) > 0
}

// 打开私聊并加载历史
async function startPrivateChat(user) {
  if (authStore.isGuest) {
    window.$message?.error('游客不能发送私聊消息')
    return
  }
  chatStore.openPrivateChat(user)

  const username = user.username || user.name
  if (!chatStore.privateMessages[username] || chatStore.privateMessages[username].length === 0) {
    chatStore.setLoading(true)
    try {
      const res = await messageApi.getPrivateHistory(username, { size: pageSize })
      if (res.data.code === 200) {
        chatStore.setPrivateMessages(username, [...res.data.data.items].reverse())
      }
    } catch (e) {
      chatStore.setError('加载私聊历史失败')
      window.$message?.error('加载私聊历史失败')
    } finally {
      chatStore.setLoading(false)
    }
  }
  scrollToBottom()
}

// 加载历史消息（群聊，游标分页）
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
  // 私聊时附接收者（必须传 username，后端按用户名查找并校验好友）
  if (isPrivateMode.value) {
    formData.append('receiver', currentChat.value.username)
  }

  try {
    const res = await fileApi.upload(formData)
    if (res.data.code !== 200) {
      window.$message?.error(res.data.message || '上传失败')
    }
  } catch (e) {
    window.$message?.error('上传失败，请重试')
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messageArea.value) {
      messageArea.value.scrollTop = messageArea.value.scrollHeight
    }
  })
}

// 监听群聊消息数量变化 → 新消息滚底（修复 $subscribe 失效）
watch(() => chatStore.messages.length, (newLen, oldLen) => {
  if (newLen > oldLen && !isPrivateMode.value) {
    scrollToBottom()
  }
})

// 监听当前会话变化，切换时滚动到底部
watch(isPrivateMode, scrollToBottom)

function handleResize() {
  isMobile.value = window.innerWidth < 1024
  if (!isMobile.value) sidebarOpen.value = true
}

onMounted(async () => {
  if (authStore.user?.id) {
    localStorage.setItem('myId', JSON.stringify(authStore.user.id))
  }
  if (authStore.user?.name) {
    localStorage.setItem('myName', authStore.user.name)
  }

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

  connect()
  window.addEventListener('resize', handleResize)
  document.addEventListener('visibilitychange', handleVisibility)
})

function handleVisibility() {
  chatStore.setPageHidden(document.hidden)
}

onUnmounted(() => {
  disconnect()
  if (unsubscribe) unsubscribe()
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('visibilitychange', handleVisibility)
  chatStore.stopTitleFlash()
})
</script>

<style scoped>
.is-active {
  background: var(--color-signal-ghost);
}
</style>

