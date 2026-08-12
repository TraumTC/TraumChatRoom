<!-- src/views/ChatView.vue — 聊天室主页面（亮色） -->
<template>
  <div class="app-h-screen flex flex-col overflow-hidden" style="background: var(--color-bg)">
    <AppHeader :show-sidebar-toggle="isMobile" @toggle-sidebar="sidebarOpen = true" />

    <div class="flex flex-1 min-h-0">
      <!-- 移动端遮罩 -->
      <div v-if="sidebarOpen && isMobile" class="fixed inset-0 z-20" style="background: rgba(0,0,0,0.5)" @click="sidebarOpen = false"></div>

      <!-- 左侧栏 -->
      <aside :class="['w-64 shrink-0 flex flex-col', isMobile ? 'fixed z-30 left-0 top-14 bottom-0 transition-transform duration-200' : '']"
             :style="isMobile ? { transform: sidebarOpen ? 'translateX(0)' : 'translateX(-100%)', background: 'var(--color-ghost)', borderRight: '1px solid var(--color-border)' }
                                 : { background: 'var(--color-ghost)', borderRight: '1px solid var(--color-border)', boxShadow: '1px 0 8px rgba(0,0,0,0.03)' }">
        <!-- 好友列表（游客隐藏） -->
        <div v-if="!authStore.isGuest" class="flex-1 flex flex-col min-h-0" style="border-bottom: 1px solid var(--color-border)">
          <FriendList @openChat="startPrivateChat" @addFriend="showAddFriend = true" @openRequests="showFriendRequests = true" />
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
               class="flex items-center gap-2 px-3 py-2 cursor-pointer user-item"
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
      <AddFriend v-if="showAddFriend" @close="showAddFriend = false" @added="onFriendAdded" />

      <!-- 好友申请弹窗 -->
      <FriendRequest v-if="showFriendRequests" @close="showFriendRequests = false" @changed="onFriendRequestChanged" />

      <!-- 右侧 -->
      <main class="flex-1 flex flex-col min-w-0">
        <!-- 私聊标签条 -->
        <PrivateChatTab v-if="!authStore.isGuest" @openChat="startPrivateChat" @open-group="chatStore.openGroupChat" />

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
          <button @click="chatStore.openGroupChat()" class="ml-auto text-xs px-2.5 py-1 rounded-full back-to-group-btn"
                  style="background: var(--color-signal-ghost); color: var(--color-signal)">
            返回群聊
          </button>
        </div>

        <!-- 聊天区 -->
        <div class="flex-1 flex flex-col min-h-0 relative" style="background: var(--color-bg)">
          <!-- 顶部状态栏（有消息时显示） -->
          <template v-if="displayMessages.length > 0">
            <div v-if="chatStore.loading" class="py-2 flex justify-center">
              <n-spin size="small" />
            </div>
            <div v-else-if="!hasMore && !isPrivateMode"
                 class="py-2 text-center text-xs" style="color: var(--color-ink-faint)">
              没有更多消息了
            </div>
          </template>

          <!-- 骨架屏（初始加载） -->
          <div v-if="chatStore.loading && displayMessages.length === 0"
               class="flex-1 px-4 py-3 space-y-4 overflow-hidden">
            <div class="flex gap-2dot5">
              <div class="skeleton-circle"></div>
              <div class="flex flex-col gap-1.5">
                <div class="skeleton-bar" style="width: 80px; height: 12px"></div>
                <div class="skeleton-bar" style="width: 200px; height: 36px; border-radius: 8px"></div>
              </div>
            </div>
            <div class="flex gap-2dot5 flex-row-reverse">
              <div class="skeleton-circle"></div>
              <div class="flex flex-col gap-1.5 items-end">
                <div class="skeleton-bar" style="width: 60px; height: 12px"></div>
                <div class="skeleton-bar" style="width: 140px; height: 36px; border-radius: 8px"></div>
              </div>
            </div>
            <div class="flex gap-2dot5">
              <div class="skeleton-circle"></div>
              <div class="flex flex-col gap-1.5">
                <div class="skeleton-bar" style="width: 90px; height: 12px"></div>
                <div class="skeleton-bar" style="width: 240px; height: 36px; border-radius: 8px"></div>
              </div>
            </div>
            <div class="flex gap-2dot5 flex-row-reverse">
              <div class="skeleton-circle"></div>
              <div class="flex flex-col gap-1.5 items-end">
                <div class="skeleton-bar" style="width: 50px; height: 12px"></div>
                <div class="skeleton-bar" style="width: 100px; height: 36px; border-radius: 8px"></div>
              </div>
            </div>
            <div class="flex gap-2dot5">
              <div class="skeleton-circle"></div>
              <div class="flex flex-col gap-1.5">
                <div class="skeleton-bar" style="width: 70px; height: 12px"></div>
                <div class="skeleton-bar" style="width: 180px; height: 36px; border-radius: 8px"></div>
              </div>
            </div>
          </div>

          <!-- 空状态 -->
          <div v-else-if="displayMessages.length === 0"
               class="flex-1 flex items-center justify-center text-sm" style="color: var(--color-ink-faint)">
            {{ isPrivateMode ? '开始私聊吧' : '还没有消息，来打个招呼吧' }}
          </div>

          <!-- 群聊消息区（常驻挂载，v-show 切换显示，保持滚动位置避免切回闪烁） -->
          <div v-show="!isPrivateMode && displayMessages.length > 0" class="flex-1 min-h-0 relative flex flex-col">
            <DynamicScroller
              ref="groupScrollerRef"
              :items="groupMessages"
              :min-item-size="52"
              :prerender="15"
              :buffer="600"
              key-field="id"
              class="flex-1 scroll-thin"
              @scroll="handleGroupScroll"
            >
              <template #default="{ item, index, active }">
                <DynamicScrollerItem
                  :item="item"
                  :active="active"
                  :data-index="index"
                  :size-dependencies="[item.content, item.messageType, item.recalled]"
                >
                  <MessageItem :message="item" />
                </DynamicScrollerItem>
              </template>
            </DynamicScroller>
          </div>

          <!-- 私聊消息区（会话切换时按 scrollerKey 重挂载） -->
          <div v-show="isPrivateMode && displayMessages.length > 0" class="flex-1 min-h-0 relative flex flex-col">
            <DynamicScroller
              ref="privateScrollerRef"
              :items="privateMessages"
              :min-item-size="52"
              :prerender="15"
              :buffer="600"
              key-field="id"
              :key="scrollerKey"
              class="flex-1 scroll-thin"
              @scroll="handlePrivateScroll"
            >
              <template #default="{ item, index, active }">
                <DynamicScrollerItem
                  :item="item"
                  :active="active"
                  :data-index="index"
                  :size-dependencies="[item.content, item.messageType, item.recalled]"
                >
                  <MessageItem :message="item" />
                </DynamicScrollerItem>
              </template>
            </DynamicScroller>
          </div>

          <!-- 新消息提示按钮 -->
          <Transition name="slide-up">
            <button v-if="showNewMessageHint"
              @click="scrollToBottomAndHideHint"
              class="absolute bottom-4 left-1/2 -translate-x-1/2 z-20 flex items-center gap-1.5 px-4 py-2 rounded-full text-xs text-white shadow-lg cursor-pointer new-msg-btn">
              <AppIcon name="chevron-down" :size="14" />
              新消息
            </button>
          </Transition>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { useWebSocketStore } from '@/stores/websocket'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChatHistory } from '@/composables/useChatHistory'
import { useFileUpload } from '@/composables/useFileUpload'
import { messageApi } from '@/api/message'
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller'
import AppHeader from '@/components/layout/AppHeader.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import MessageItem from '@/components/chat/MessageItem.vue'
import MessageInput from '@/components/chat/MessageInput.vue'
import PrivateChatTab from '@/components/chat/PrivateChatTab.vue'
import FriendList from '@/components/friend/FriendList.vue'
import AddFriend from '@/components/friend/AddFriend.vue'
import FriendRequest from '@/components/friend/FriendRequest.vue'

const chatStore = useChatStore()
const authStore = useAuthStore()
const wsStore = useWebSocketStore()
const { connect, disconnect } = useWebSocket()

// 历史消息加载、分页、智能滚动逻辑（群聊/私聊双容器）
const {
  groupScrollerRef, privateScrollerRef, hasMore, isNearBottom, showNewMessageHint,
  currentChat, isPrivateMode, groupMessages, privateMessages, displayMessages,
  scrollToBottomAndHideHint, handleGroupScroll, handlePrivateScroll,
  startPrivateChat, loadInitialHistory,
} = useChatHistory(chatStore, authStore)

// 文件上传逻辑
const { handleFileUpload } = useFileUpload(chatStore)

// 视图级 UI 状态
const showAddFriend = ref(false)
const showFriendRequests = ref(false)
const sidebarOpen = ref(false)
const isMobile = ref(window.innerWidth < 1024)

const onlineUsers = computed(() => chatStore.onlineUsers)
const onlineCount = computed(() => onlineUsers.value.length)

// 私聊会话切换时强制私聊 DynamicScroller 重挂载（重置滚动与缓存；群聊容器常驻不重挂载）
// 注意：不包含消息数量，否则每条新消息都会触发重挂载
const scrollerKey = computed(() => {
  if (isPrivateMode.value) {
    return `private-${currentChat.value.username || ''}`
  }
  return 'group'
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

function onFriendAdded() {
  chatStore.incrementFriendListVersion()
}

function onFriendRequestChanged() {
  chatStore.incrementFriendListVersion()
}

function handleResize() {
  isMobile.value = window.innerWidth < 1024
  if (!isMobile.value) sidebarOpen.value = true
}

function handleVisibility() {
  chatStore.setPageHidden(document.hidden)
}

onMounted(async () => {
  if (authStore.user?.id) {
    localStorage.setItem('myId', JSON.stringify(authStore.user.id))
  }
  if (authStore.user?.name) {
    localStorage.setItem('myName', authStore.user.name)
  }

  // 游客：强制回到群聊并清除已持久化的私聊会话（游客不恢复私聊）
  if (authStore.isGuest) {
    chatStore.resetSessionState()
  }

  await loadInitialHistory()
  // 拉取离线期间的未读汇总（游客无私聊，跳过），合并进本地未读状态
  if (!authStore.isGuest) {
    messageApi.getUnreadSummary()
      .then(res => {
        if (res.data.code === 200) {
          chatStore.mergeUnreadSummary(res.data.data)
          // 刷新恢复的当前私聊会话视为已读（本地清零 + 有未读则推进后端游标）
          chatStore.clearCurrentUnread()
        }
      })
      .catch(() => { /* 离线未读拉取失败不阻塞聊天 */ })
  }
  connect()
  window.addEventListener('resize', handleResize)
  document.addEventListener('visibilitychange', handleVisibility)
})

onUnmounted(() => {
  disconnect()
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('visibilitychange', handleVisibility)
  chatStore.stopTitleFlash()
})
</script>

<style scoped>
.is-active {
  background: var(--color-signal-ghost);
}

.back-to-group-btn {
  transition: all 0.2s ease;
}
.back-to-group-btn:hover {
  background: var(--color-signal) !important;
  color: #fff !important;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.2);
}

/* --- 骨架屏脉冲动画 --- */
.skeleton-circle {
  width: 40px;
  height: 40px;
  border-radius: 9999px;
  background: var(--color-hover);
  flex-shrink: 0;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}
.skeleton-bar {
  background: var(--color-hover);
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}
@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* --- 新消息提示按钮 --- */
.new-msg-btn {
  background: var(--color-signal);
  transition: all 0.2s ease;
}
.new-msg-btn:hover {
  background: var(--color-signal-deep);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.4);
  transform: translateX(-50%) translateY(-2px);
}

/* --- 过渡动画 --- */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(12px);
}
</style>

